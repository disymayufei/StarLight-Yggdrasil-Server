package moe.yushi.yggdrasil_mock.network;

import moe.yushi.yggdrasil_mock.user.YggdrasilUser;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConfigurationProperties(prefix = "yggdrasil.rate-limit")
public class RateLimiter {

	private final Map<YggdrasilUser, AtomicLong> timings = new ConcurrentHashMap<>();
	private final Map<String, AtomicLong> stringTimings = new ConcurrentHashMap<>();

	private Duration limitDuration;
	private Duration emailLimitDuration;

	public boolean tryAccess(YggdrasilUser key) {
		AtomicLong lastAccess = timings.get(key);
		long now = System.currentTimeMillis();

		if (lastAccess == null) {
			// if putIfAbsent() returns a non-null value,
			// which means another thread who is also trying to access this key
			// has put an AtomicLong into the map between our last get() call and this putIfAbsent() call,
			// this access must be rate-limited, as the duration between two calls is really a short time
			// (and the value of the AtomicLong is within the duration).
			return timings.putIfAbsent(key, new AtomicLong(now)) == null;
		}

		long lastAccessTime = lastAccess.get();
		if (now - lastAccessTime > limitDuration.toMillis()) {
			// same as above
			// if the CAS operation fails, this access must be rate-limited.
			return lastAccess.compareAndSet(lastAccessTime, now);
		} else {
			return false;
		}
	}

	public boolean tryAccess(String key) {
		AtomicLong lastAccess = stringTimings.get(key);
		long now = System.currentTimeMillis();

		if (lastAccess == null) {
			return stringTimings.putIfAbsent(key, new AtomicLong(now)) == null;
		}

		long lastAccessTime = lastAccess.get();
		if (now - lastAccessTime > emailLimitDuration.toMillis()) {
			return lastAccess.compareAndSet(lastAccessTime, now);
		} else {
			return false;
		}
	}

	public Duration getLimitDuration() {
		return limitDuration;
	}

	public void setLimitDuration(Duration limitDuration) {
		this.limitDuration = limitDuration;
	}

	public Duration getEmailLimitDuration() {
		return emailLimitDuration;
	}

	public void setEmailLimitDuration(Duration emailLimitDuration) {
		this.emailLimitDuration = emailLimitDuration;
	}
}
