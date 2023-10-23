package moe.yushi.yggdrasil_mock.texture;

import com.google.common.collect.MapMaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriBuilder;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public class Texture {

	public final String hash;
	public final byte[] data;
	public final String url;

	public Texture(String hash, byte[] data, String url) {
		this.hash = requireNonNull(hash);
		this.data = requireNonNull(data);
		this.url = requireNonNull(url);
	}

	public String getHash() {
		return hash;
	}

	public byte[] getData() {
		return data;
	}
}
