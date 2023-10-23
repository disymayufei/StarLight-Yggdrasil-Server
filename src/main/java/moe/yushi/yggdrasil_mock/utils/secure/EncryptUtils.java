package moe.yushi.yggdrasil_mock.utils.secure;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import lombok.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

public final class EncryptUtils {

	private static final String CONST_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	private static final Argon2 argon2 = Argon2Factory.create();
	private EncryptUtils() {}

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

	public static String getArgon2Hash(@NonNull String pwd) {
		return argon2.hash(22, 65536, 5, pwd.getBytes(StandardCharsets.UTF_8));
	}

	public static boolean verifyArgon2Hash(String hash, String pwd) {
		if (pwd == null || hash == null) {
			return false;
		}

		return argon2.verify(hash, pwd.getBytes(StandardCharsets.UTF_8));
	}
}
