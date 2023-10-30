package moe.yushi.yggdrasil_mock.utils.image;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Random;

public class ImageUtils {
    private static final File rootDir = new File("verifyImage");

    @Nullable
    public static BufferedImage getVerifyImage() {
        File[] images = rootDir.listFiles((dir, name) -> (dir.isFile() || name.endsWith(".jpg") || name.endsWith("jpeg") || name.endsWith("jiff") || name.endsWith(".png") || name.endsWith("bmp")));

        if (images == null) {
            return null;
        }

        int index = new Random().nextInt(images.length);

        try {
            return ImageIO.read(images[index]);
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static byte[] encodeImage(BufferedImage image) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            ImageIO.write(image, "jpg", outputStream);
            return outputStream.toByteArray();
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static BufferedImage rotateImage(BufferedImage bufferedImage, int angel) {
        if (bufferedImage == null) {
            return null;
        }
        if (angel < 0) {
            // 将负数角度，纠正为正数角度
            angel = angel + 360;
        }
        int imageWidth = bufferedImage.getWidth(null);
        int imageHeight = bufferedImage.getHeight(null);

        // 获取原始图片的透明度
        int type = bufferedImage.getColorModel().getTransparency();
        BufferedImage newImage;
        newImage = new BufferedImage(imageWidth, imageHeight, type);
        Graphics2D graphics = newImage.createGraphics();
        // 旋转角度
        graphics.rotate(Math.toRadians(angel), (double) imageWidth / 2, (double) imageHeight / 2);
        // 绘图
        graphics.drawImage(bufferedImage, null, null);
        return newImage;
    }
}
