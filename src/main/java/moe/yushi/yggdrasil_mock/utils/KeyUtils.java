package moe.yushi.yggdrasil_mock.utils;

import java.security.*;
import java.util.Base64;

public final class KeyUtils {

	private static final String CONST_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	private KeyUtils() {}

	public static KeyPair generateKey() {
		try {
			var gen = KeyPairGenerator.getInstance("RSA");
			gen.initialize(4096, new SecureRandom());
			return gen.genKeyPair();
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public static String toPEMPublicKey(PublicKey key) {
		var encoded = key.getEncoded();
		return "-----BEGIN PUBLIC KEY-----\n" +
				Base64.getMimeEncoder(76, new byte[] { '\n' }).encodeToString(encoded) +
				"\n-----END PUBLIC KEY-----\n";
	}

	/**
	 * 生成指定长度的随机密码
	 * @return 生成的密码
	 */
	public static String gen(int length){
		SecureRandom random = new SecureRandom();
		StringBuilder resultBuilder = new StringBuilder();

		for(int i = 0; i < length; i++){
			resultBuilder.append(CONST_ALPHABET.charAt(random.nextInt(CONST_ALPHABET.length())));
		}

		return resultBuilder.toString();
	}
}
