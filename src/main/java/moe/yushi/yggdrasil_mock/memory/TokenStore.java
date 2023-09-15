package moe.yushi.yggdrasil_mock.memory;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import moe.yushi.yggdrasil_mock.user.YggdrasilCharacter;
import moe.yushi.yggdrasil_mock.user.YggdrasilUser;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static java.lang.Math.max;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static moe.yushi.yggdrasil_mock.utils.UUIDUtils.randomUnsignedUUID;

@Component
@ConfigurationProperties(prefix = "yggdrasil.token")
public class TokenStore {

	private static final int MAX_TOKEN_COUNT = 100_000;

	public enum AvailableLevel {
		COMPLETE, PARTIAL;
	}

	public class Token {
		private long id;
		private String clientToken;
		private String accessToken;
		private long createdAt;
		private Optional<YggdrasilCharacter> boundCharacter;
		private YggdrasilUser user;

		private Token() {}

		/** Assuming isFullyExpired() returns true */
		private boolean isCompleteValid() {
			if (enableTimeToPartiallyExpired && System.currentTimeMillis() > createdAt + timeToPartiallyExpired.toMillis())
				return false;

			return !onlyLastSessionAvailable || this == lastAcquiredToken.get(user);
		}

		private boolean isFullyExpired() {
			if (System.currentTimeMillis() > createdAt + timeToFullyExpired.toMillis())
				return true;

			AtomicLong latestRevoked = notBefore.get(user);

			return latestRevoked != null && id < latestRevoked.get();
		}

		public String getClientToken() {
			return clientToken;
		}

		public String getAccessToken() {
			return accessToken;
		}

		public long getCreatedAt() {
			return createdAt;
		}

		public Optional<YggdrasilCharacter> getBoundCharacter() {
			return boundCharacter;
		}

		public YggdrasilUser getUser() {
			return user;
		}
	}

	private Duration timeToFullyExpired;

	private boolean enableTimeToPartiallyExpired;
	private Duration timeToPartiallyExpired;

	private boolean onlyLastSessionAvailable;

	private final AtomicLong tokenIdGen = new AtomicLong();
	private final ConcurrentHashMap<YggdrasilUser, AtomicLong> notBefore = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<YggdrasilUser, Token> lastAcquiredToken = new ConcurrentHashMap<>();  // 储存某用户上次请求得到的的Token
	private final ConcurrentLinkedHashMap<String, Token> accessToken2token = new ConcurrentLinkedHashMap.Builder<String, Token>()  // 将字符串形式的accessToken转化为Token对象
			.maximumWeightedCapacity(MAX_TOKEN_COUNT)
			.listener((k, v) -> lastAcquiredToken.remove(v.user, v))
			.build();

	private void removeToken(Token token) {
		/*
		 * 逻辑：从数据库中寻找相应的Token
		 * 如果存在该Token，给删了
		 */
		accessToken2token.remove(token.accessToken);
		lastAcquiredToken.remove(token.user, token);
	}

	public Optional<Token> authenticate(String accessToken, @Nullable String clientToken, AvailableLevel availableLevel) {
		/*
		 * 第一步替换成，从SQL数据库中拿数据
		 */
		var token = accessToken2token.getQuietly(accessToken);
		if (token == null)
			return empty();

		if (token.isFullyExpired()) {
			removeToken(token);
			return empty();
		}

		if (clientToken != null && !clientToken.equals(token.clientToken))
			return empty();

		switch (availableLevel) {
			case COMPLETE:
				if (token.isCompleteValid()) {
					return of(token);
				} else {
					return empty();
				}

			case PARTIAL:
				return of(token);

			default:
				throw new IllegalArgumentException("Unknown AvailableLevel: " + availableLevel);
		}
	}

	/**
	 * @param checker
	 * returning false will cause the method to return empty, throwing an exception is also ok
	 */
	public Optional<Token> authenticateAndConsume(String accessToken, @Nullable String clientToken, AvailableLevel availableLevel, Predicate<Token> checker) {
		return authenticate(accessToken, clientToken, availableLevel)
				.flatMap(token -> {
					if (!checker.test(token))
						// the operation cannot be performed
						return empty();

					if (accessToken2token.remove(accessToken) == token) {
						// we have won the remove() race
						lastAcquiredToken.remove(token.user, token);
						return of(token);
					} else {
						// another thread won the race and consumed the token
						return empty();
					}
				});
	}

	public Token acquireToken(YggdrasilUser user, @Nullable String clientToken, @Nullable YggdrasilCharacter selectedCharacter) {
		var token = new Token();
		token.accessToken = randomUnsignedUUID();
		if (selectedCharacter == null) {
			if (user.getCharacters().size() == 1) {
				token.boundCharacter = of(user.getCharacters().get(0));
			} else {
				token.boundCharacter = empty();
			}
		} else {
			if (!user.getCharacters().contains(selectedCharacter)) {
				throw new IllegalArgumentException("the character to select doesn't belong to the user");
			}
			token.boundCharacter = of(selectedCharacter);
		}
		token.clientToken = clientToken == null ? randomUnsignedUUID() : clientToken;
		token.createdAt = System.currentTimeMillis();
		token.user = user;
		token.id = tokenIdGen.getAndIncrement();

		/*
		 * 这里换成往MySQL中塞数据
		 */
		accessToken2token.put(token.accessToken, token);
		// the token we just put into `accessToken2token` may have been flush out from cache here,
		// and the listener may be notified before the token is put into `lastAcquiredToken`
		lastAcquiredToken.put(user, token);

		if (!accessToken2token.containsKey(token.accessToken))
			// if so, remove the token from `lastAcquiredToken`
			lastAcquiredToken.remove(user, token);

		return token;
	}

	public void revokeAll(YggdrasilUser user) {
		/*
		 * 从MySQL中找到对应的数据，然后全给扬了
		 */
		notBefore.computeIfAbsent(user, k -> new AtomicLong())
				.getAndUpdate(original -> max(original, tokenIdGen.get()));
	}

	public int tokensCount() {
		/*
		 * 拿SQL语句硬数吧
		 */
		return accessToken2token.size();
	}

	public Duration getTimeToPartiallyExpired() {
		return timeToPartiallyExpired;
	}

	public void setTimeToPartiallyExpired(Duration timeToPartiallyExpired) {
		this.timeToPartiallyExpired = timeToPartiallyExpired;
	}

	public Duration getTimeToFullyExpired() {
		return timeToFullyExpired;
	}

	public void setTimeToFullyExpired(Duration timeToFullyExpired) {
		this.timeToFullyExpired = timeToFullyExpired;
	}

	public boolean isEnableTimeToPartiallyExpired() {
		return enableTimeToPartiallyExpired;
	}

	public void setEnableTimeToPartiallyExpired(boolean enableTimeToPartiallyExpired) {
		this.enableTimeToPartiallyExpired = enableTimeToPartiallyExpired;
	}

	public boolean isOnlyLastSessionAvailable() {
		return onlyLastSessionAvailable;
	}

	public void setOnlyLastSessionAvailable(boolean onlyLastSessionAvailable) {
		this.onlyLastSessionAvailable = onlyLastSessionAvailable;
	}
}
