package moe.yushi.yggdrasil_mock.database.mysql;

import moe.yushi.yggdrasil_mock.database.mysql.MysqlDatabase;
import moe.yushi.yggdrasil_mock.yggdrasil.YggdrasilCharacter;
import moe.yushi.yggdrasil_mock.yggdrasil.YggdrasilUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static moe.yushi.yggdrasil_mock.utils.UUIDUtils.randomUnsignedUUID;

@SuppressWarnings({"SqlNoDataSourceInspection", "SqlResolve"})
@Component
@ConfigurationProperties(prefix = "yggdrasil.token")
public final class TokenStore {
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MysqlDatabase mysqlDatabase;

	@PostConstruct
	private void init() {
		this.jdbcTemplate = mysqlDatabase.getJdbcTemplate();
	}

	public enum AvailableLevel {
		COMPLETE, PARTIAL;
	}

	public class Token {
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

			return !onlyLastSessionAvailable;
		}

		private boolean isFullyExpired() {
			return System.currentTimeMillis() > createdAt + timeToFullyExpired.toMillis();
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

	private void removeToken(Token token) {
		String sql = "DELETE FROM Token WHERE access_token=?";
		jdbcTemplate.update(sql, token.accessToken);
	}

	public Optional<Token> authenticate(String accessToken, @Nullable String clientToken, AvailableLevel availableLevel) {
		String sql = "SELECT * FROM Token WHERE access_token=?";
		Token token;

		try {
			var resultList = jdbcTemplate.queryForList(sql, accessToken);
			if (!resultList.isEmpty()) {
				var result = resultList.get(0);

				token = new Token();
				token.accessToken = (String) result.get("access_token");
				token.clientToken = (String) result.get("client_token");
				token.createdAt = ((BigInteger) result.get("created_at")).longValue();

				Optional<YggdrasilUser> user = mysqlDatabase.findUserByUID(((BigInteger) result.get("user_uid")).longValue());
				if (user.isEmpty()) {
					return empty();
				}

				token.user = user.get();
				token.boundCharacter = mysqlDatabase.findCharacterByUUID(UUID.fromString((String) result.get("character")));
			}
			else {
				return empty();
			}

		}
		catch (EmptyResultDataAccessException e) {
			return empty();
		}

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

					removeToken(token);
					return of(token);
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

		String sql = "INSERT INTO Token(`client_token`, `access_token`, `created_at`, `character`, `user_uid`) VALUES(?, ?, ?, ?, ?)";

		String uuid = token.boundCharacter.map(yggdrasilCharacter -> yggdrasilCharacter.getUuid().toString()).orElse(null);
		jdbcTemplate.update(sql, token.clientToken, token.accessToken, token.createdAt, uuid, token.user.getUID());

		return token;
	}

	public void revokeAll(YggdrasilUser user) {
		String sql = "DELETE FROM Token WHERE user_uid=?";
		jdbcTemplate.update(sql, user.getUID());
	}

	public int tokensCount() {
		String sql = "SELECT COUNT(*) as count FROM Token";
		Integer num = jdbcTemplate.queryForObject(sql, Integer.class);
		return num == null ? 0 : num;
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
