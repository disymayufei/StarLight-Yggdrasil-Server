package moe.yushi.yggdrasil_mock.network.router;

import moe.yushi.yggdrasil_mock.database.mysql.MysqlDatabase;
import moe.yushi.yggdrasil_mock.database.redis.RedisService;
import moe.yushi.yggdrasil_mock.network.RateLimiter;
import moe.yushi.yggdrasil_mock.utils.secure.EncryptUtils;
import moe.yushi.yggdrasil_mock.texture.ModelType;
import moe.yushi.yggdrasil_mock.utils.MailUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.TooManyListenersException;
import java.util.UUID;

@Validated
@RestController
public class StarLightRouter {

    private @Autowired MailUtils mailUtils;
    private @Autowired RateLimiter rateLimiter;
    private @Autowired RedisService redisService;
    private @Autowired MysqlDatabase mysqlDatabase;

    /**
     * 获取邮箱验证码的请求
     * @param email 请求验证码的邮箱地址
     * @return 请求的结果
     */
    @GetMapping("/sendVerifyCode/{email}")
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

    @PostMapping("/starlight/login")
    public ResponseEntity<?> login(@RequestParam String email, @RequestParam String pwd, @RequestParam String clientId, @RequestParam String verifyCode) {
        var correctVerifyCode = redisService.get("verifyCode-" + clientId);
        Map<String, String> response;

        if (correctVerifyCode != null && correctVerifyCode.equals(verifyCode)) {
            String hashedPwd = EncryptUtils.getArgon2Hash(pwd + "sls-skin");

            if (mysqlDatabase.verifyUserPassword(email, hashedPwd)) {
                response = new HashMap<>(){{
                    put("status", "success");
                }};

                return new ResponseEntity<>(response, HttpStatus.OK);
            }
            else {
                response = new HashMap<>(){{
                    put("status", "failed");
                    put("errorMessage", "Wrong password!");
                }};

                return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
            }
        }
        else {
            response = new HashMap<>(){{
                put("status", "failed");
                put("errorMessage", "Wrong verify code!");
            }};

            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }
    }

    @GetMapping("/test")
    public String test() {

        System.err.println(mysqlDatabase.findUserByEmail("example@a.com"));

        return mysqlDatabase.findUserByEmail("example@a.com").toString();
    }

    @GetMapping("/test/addUser")
    public String testAdd() {
        mysqlDatabase.addUser("example@a.com", UUID.randomUUID().toString(), EncryptUtils.getArgon2Hash("pwd") );

        return "User added!";
    }

    @GetMapping("/test/addCharacter")
    public String testAddCharacter() throws TooManyListenersException {
        mysqlDatabase.addNewCharacter(UUID.randomUUID().toString(), "hahaha", ModelType.STEVE, 1);

        return "Character added!";
    }
}
