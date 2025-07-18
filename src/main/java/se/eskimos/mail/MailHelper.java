package se.eskimos.mail;

import java.util.Properties;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import se.eskimos.helpers.ConfigHelper;
import se.eskimos.helpers.TextHelper;

public class MailHelper {
    private final ConfigHelper configHelper;

    public MailHelper(ConfigHelper configHelper) {
        this.configHelper = configHelper;
    }

    public void sendMail(String subject, String body) {
        if (!configHelper.isSendMail()) {
            return;
        }
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", configHelper.getSmtpHost());
            props.put("mail.smtp.port", configHelper.getSmtpPort());
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.socketFactory.port", configHelper.getSmtpPort());
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");

            Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(configHelper.getSentFrom(), configHelper.getAppPasswd());
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(configHelper.getSentFrom()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(configHelper.getSendTo()));
            message.setSubject(subject);
            message.setText(body);

            Transport.send(message);
        } catch (MessagingException e) {
            e.printStackTrace();
            se.eskimos.log.LogHelper.LogError(TextHelper.FAILED_TO_SEND_EXCEPTION_MAIL + e.getMessage());
        }
    }
} 