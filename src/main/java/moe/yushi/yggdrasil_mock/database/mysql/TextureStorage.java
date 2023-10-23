package moe.yushi.yggdrasil_mock.database.mysql;

import moe.yushi.yggdrasil_mock.exception.FileUploadFailedException;
import moe.yushi.yggdrasil_mock.texture.Texture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriBuilder;

import javax.annotation.PostConstruct;
import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class TextureStorage {
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MysqlDatabase mysqlDatabase;

    @Autowired
    private ApplicationContext ctx;

    @Value("#{rootUrl}")
    private Supplier<UriBuilder> rootUrl;

    private final File rootDir = new File("Texture");
    private final Logger logger = LoggerFactory.getLogger(TextureStorage.class);

    @PostConstruct
    private void init() {
        this.jdbcTemplate = mysqlDatabase.getJdbcTemplate();
    }

    public Optional<Texture> getTexture(String hash) {
        if (!rootDir.isDirectory()) {
            rootDir.mkdirs();
        }

        Texture texture = null;
        File textureFile = new File(rootDir, hash + ".png");
        if (textureFile.isFile()) {
            try {
                texture = loadTexture(new FileInputStream(textureFile));
            } catch (Exception e) {
                logger.error("A texture encountered an error while loading:");
                e.printStackTrace();
            }
        }

        return Optional.ofNullable(texture);
    }

    public void deleteTexture(String hash) {
        if (!rootDir.isDirectory()) {
            return;
        }

        File textureFile = new File(rootDir, hash + ".png");
        if (textureFile.isFile()) {
            textureFile.delete();
        }
    }

    public void uploadTexture(Texture texture) throws FileUploadFailedException {
        if (!rootDir.isDirectory()) {
            rootDir.mkdirs();
        }

        File textureFile = new File(rootDir, texture.getHash() + ".png");
        if (!textureFile.isFile()) {
            try {
                textureFile.createNewFile();
                try (FileOutputStream outputStream = new FileOutputStream(textureFile)) {
                    outputStream.write(texture.getData());
                }
            }
            catch (IOException e) {
                throw new FileUploadFailedException(e.getMessage());
            }
        }
    }

    public Texture loadTexture(InputStream in) throws IOException {
        var img = ImageIO.read(in);
        if (img == null) {
            throw new IIOException("No image found");
        }

        var hash = computeTextureHash(img);

        var url = rootUrl.get().path("/textures/{hash}").build(hash).toString();
        var buf = new ByteArrayOutputStream();
        ImageIO.write(img, "png", buf);

        return new Texture(hash, buf.toByteArray(), url);
    }

    public Texture loadTexture(String url) throws IOException {
        try (var in = ctx.getResource(url).getInputStream()) {
            return loadTexture(in);
        }
    }

    public static String computeTextureHash(BufferedImage img) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        int width = img.getWidth();
        int height = img.getHeight();
        byte[] buf = new byte[4096];

        putInt(buf, 0, width);
        putInt(buf, 4, height);
        int pos = 8;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                putInt(buf, pos, img.getRGB(x, y));
                if (buf[pos + 0] == 0) {
                    buf[pos + 1] = buf[pos + 2] = buf[pos + 3] = 0;
                }
                pos += 4;
                if (pos == buf.length) {
                    pos = 0;
                    digest.update(buf, 0, buf.length);
                }
            }
        }
        if (pos > 0) {
            digest.update(buf, 0, pos);
        }

        byte[] sha256 = digest.digest();
        return String.format("%0" + (sha256.length << 1) + "x", new BigInteger(1, sha256));
    }

    private static void putInt(byte[] array, int offset, int num) {
        array[offset + 0] = (byte) (num >> 24 & 0xff);
        array[offset + 1] = (byte) (num >> 16 & 0xff);
        array[offset + 2] = (byte) (num >> 8 & 0xff);
        array[offset + 3] = (byte) (num >> 0 & 0xff);
    }

}
