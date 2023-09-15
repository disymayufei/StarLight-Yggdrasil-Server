package moe.yushi.yggdrasil_mock.utils;

import lombok.NonNull;

import java.util.UUID;

public final class UUIDUtils {
	private UUIDUtils() {}

	public static String unsign(UUID uuid) {
		return uuid.toString().replace("-", "");
	}

	public static String unsign(String uuid) {
		return uuid.replace("-", "");
	}

	public static UUID toUUID(String uuid) {
		switch (uuid.length()) {
			case 36:
				return UUID.fromString(uuid);

			case 32:
				return UUID.fromString(uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-" + uuid.substring(12, 16) + "-" + uuid.substring(16, 20) + "-" + uuid.substring(20, 32));

			default:
				throw new IllegalArgumentException("Invalid UUID: " + uuid);
		}
	}

	/**
	 * 获得某玩家的StarLight UUID，需要遵循以下规范：
	 * 高64bit位为玩家的唯一识别码，如果从QQ绑定则必须为QQ号，否则需要生成一个8bit的唯一特征码
	 * 而后32bit位被称为备用位，当高64为发生碰撞时，可以通过该位解除碰撞，否则以全0填充
	 * 最后的低32bit位被称为编码位，用于区分同玩家名下的不同账号
	 * @param highBits 高64bit位
	 * @param backupBits 备用位，长度为32bit
	 * @param code 编码位，长度为32bit
	 * @return 专属于StarLight玩家的唯一UUID
	 */
	@NonNull
	public static UUID getStarLightUUID(long highBits, int backupBits, int code) {
		long lowBits = ((long) backupBits << 4) + code;
		return new UUID(highBits, lowBits);
	}

	public static String randomUnsignedUUID() {
		return unsign(UUID.randomUUID());
	}
}
