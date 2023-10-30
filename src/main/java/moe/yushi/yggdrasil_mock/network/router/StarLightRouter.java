package moe.yushi.yggdrasil_mock.network.router;

import moe.yushi.yggdrasil_mock.database.memory.StringCache;
import moe.yushi.yggdrasil_mock.database.mysql.MysqlDatabase;
import moe.yushi.yggdrasil_mock.exception.UserAlreadyExistedException;
import moe.yushi.yggdrasil_mock.network.RateLimiter;
import moe.yushi.yggdrasil_mock.texture.ModelType;
import moe.yushi.yggdrasil_mock.utils.MailUtils;
import moe.yushi.yggdrasil_mock.utils.image.ImageUtils;
import moe.yushi.yggdrasil_mock.utils.secure.EncryptUtils;
import moe.yushi.yggdrasil_mock.yggdrasil.YggdrasilUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.awt.image.BufferedImage;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Validated
@RestController
public class StarLightRouter {

    private @Autowired MailUtils mailUtils;
    private @Autowired RateLimiter rateLimiter;
    private @Autowired MysqlDatabase mysqlDatabase;

    private final AtomicLong clientId = new AtomicLong(0);

    /**
     * 获取邮箱验证码的请求
     * @param email 请求验证码的邮箱地址
     * @return 请求的结果
     */
    @GetMapping("/starlight/sendVerifyCode/{email}")
    public ResponseEntity<?> sendEmail(@PathVariable String email) {
        Map<String, String> response;

        if (!rateLimiter.tryAccess(email)) {
            response = new HashMap<>(){{
                put("status", "failed");
                put("errorMessage", "Exceed speed limit.");
                put("receiver", email);
            }};

            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }

        mailUtils.sendVerifyCodeMail(email);

        response = new HashMap<>(){{
            put("status", "success");
            put("receiver", email);
        }};

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/starlight/verifyImage")
    public ResponseEntity<?> requireVerifyImage() {
        Map<String, Object> response;

        var degree = new SecureRandom().nextInt(300) + 30;

        BufferedImage verifyImage = ImageUtils.getVerifyImage();
        if (verifyImage == null) {
            response = new HashMap<>(){{
                put("status", "failed");
                put("errorMessage", "Failed to get verify image");
            }};

            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        else {
            var originWidth = verifyImage.getWidth();
            var originHeight = verifyImage.getHeight();

            var rotatedImage = ImageUtils.rotateImage(verifyImage, degree);

            var width = rotatedImage.getWidth();
            var height = rotatedImage.getHeight();

            byte[] data = ImageUtils.encodeImage(rotatedImage);

            if (data != null) {
                StringCache.INSTANCE.put("verifyDegree-" + clientId.incrementAndGet(), Integer.toString(degree));

                response = new HashMap<>(){{
                    put("status", "success");
                    put("origin_width", originWidth);
                    put("origin_height", originHeight);
                    put("width", width);
                    put("height", height);
                    put("client_id", clientId.get());
                    put("image", Base64.getEncoder().encodeToString(data));
                }};

                return new ResponseEntity<>(response, HttpStatus.OK);
            }
            else {
                response = new HashMap<>(){{
                    put("status", "failed");
                    put("errorMessage", "Failed to encode verify image");
                }};

                return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    @PostMapping("/starlight/login")
    public ResponseEntity<?> login(@RequestParam String email, @RequestParam String pwd, @RequestParam String clientId, @RequestParam int verifyDegree) {
        var correctVerifyCode = Integer.parseInt(StringCache.INSTANCE.get("verifyDegree-" + clientId));
        Map<String, String> response;

        if (Math.abs(verifyDegree - correctVerifyCode) <= 5) {
            String hashedPwd = EncryptUtils.getArgon2Hash(pwd);

            if (mysqlDatabase.verifyUserPassword(email, hashedPwd)) {
                response = new HashMap<>(){{
                    put("status", "success");
                }};

            }
            else {
                response = new HashMap<>(){{
                    put("status", "failed");
                    put("errorMessage", "Wrong password!");
                }};

            }
        }
        else {
            response = new HashMap<>(){{
                put("status", "failed");
                put("errorMessage", "Wrong verify code!");
            }};

        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/test")
    public String test() {

        System.err.println(mysqlDatabase.findUserByEmail("disymayufei@yeah.net"));

        return mysqlDatabase.findUserByEmail("disymayufei@yeah.net").toString();
    }

    @GetMapping("/test/addUser")
    public String testAdd() {
        try {
            mysqlDatabase.addUser(1481567451L, UUID.randomUUID().toString(), EncryptUtils.getArgon2Hash("pwd"));
        }
        catch (UserAlreadyExistedException ignored) {}
        mysqlDatabase.bindEmail(1481567451L, "disymayufei@yeah.net");

        return "User added!";
    }

    @GetMapping("/test/addCharacter")
    public String testAddCharacter() throws TooManyListenersException {
        Optional<YggdrasilUser> user = mysqlDatabase.findUserByQQ(1481567451L);
        if (user.isPresent()) {
            mysqlDatabase.addNewCharacter(UUID.randomUUID().toString(), "Disy920", ModelType.STEVE, user.get().getUID());
            return "Character added!";
        }

        return "User not exist!";
    }
}
