package moe.yushi.yggdrasil_mock.utils;

import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import moe.yushi.yggdrasil_mock.database.memory.StringCache;
import moe.yushi.yggdrasil_mock.utils.secure.EncryptUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.mail.internet.MimeMessage;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

@Service
public final class MailUtils {
    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String from;

    public void sendSimpleMail(MailReceiver receiver) {
        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        // 发件人
        simpleMailMessage.setFrom(from);
        // 收件人
        simpleMailMessage.setTo(receiver.getReceiver());
        // 邮件主题
        simpleMailMessage.setSubject(receiver.getTitle());
        // 邮件内容
        simpleMailMessage.setText(receiver.getContent());

        mailSender.send(simpleMailMessage);
    }

    public void sendHtmlMail(MailReceiver receiver) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper messageHelper = new MimeMessageHelper(message, true);
            //邮件发送人
            messageHelper.setFrom(from);
            //邮件接收人
            messageHelper.setTo(receiver.getReceiver());
            //邮件主题
            message.setSubject(receiver.getTitle());
            //邮件内容
            messageHelper.setText(receiver.getContent(), true);

            mailSender.send(message);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendVerifyCodeMail(String receiver) {
        String message = "请确认您刚刚在TLSL玩家中心申请过验证码，若非是您本人的行为，请忽略!";

        String code = EncryptUtils.gen(6);

        StringCache.INSTANCE.put("email-" + receiver, code);
        StringCache.INSTANCE.expireKey("email-" + receiver, 5, TimeUnit.MINUTES);

        Context context = new Context();
        context.setVariable("message", message);
        context.setVariable("code", code);
        context.setVariable("year", Calendar.getInstance().get(Calendar.YEAR));
        String mail = templateEngine.process("mail.html", context);

        sendHtmlMail(new MailReceiver(receiver, "The Land of StarLight 皮肤站注册验证", mail));
    }

    public String getVerifyCode(String email) {
        return StringCache.INSTANCE.get("email-" + email);
    }

    @RequiredArgsConstructor
    @Data
    public static class MailReceiver {
        @NonNull
        private final String receiver;
        @NonNull
        private final String title;
        @NonNull
        private final String content;
    }
}
