package se.eskimos.mail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import se.eskimos.helpers.ConfigHelper;

public class MailExceptionBuffer {
    private static final List<String> exceptionBuffer = Collections.synchronizedList(new ArrayList<>());
    private static ConfigHelper configHelper;
    private static String subject = "IPTV-Recorder Exception Report";

    public static void setConfig(ConfigHelper cfg) {
        configHelper = cfg;
    }

    public static void addException(String context, Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append("Context: ").append(context).append("\n");
        sb.append("Exception: ").append(e.toString()).append("\n");
        for (StackTraceElement el : e.getStackTrace()) {
            sb.append("    at ").append(el.toString()).append("\n");
        }
        exceptionBuffer.add(sb.toString());
    }

    public static void addException(String context, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("Context: ").append(context).append("\n");
        sb.append("Message: ").append(message).append("\n");
        exceptionBuffer.add(sb.toString());
    }

    public static void flushAndSend() {
        if (configHelper == null || !configHelper.isSendMail() || exceptionBuffer.isEmpty()) return;
        StringBuilder mailBody = new StringBuilder();
        mailBody.append("The following exceptions occurred during program execution:\n\n");
        synchronized (exceptionBuffer) {
            for (String ex : exceptionBuffer) {
                mailBody.append(ex).append("\n-----------------------------\n");
            }
            exceptionBuffer.clear();
        }
        new MailHelper(configHelper).sendMail(subject, mailBody.toString());
    }

    public static void flushAndSendImmediately(String context, Exception e) {
        addException(context, e);
        flushAndSend();
    }

    public static void flushAndSendImmediately(String context, String message) {
        addException(context, message);
        flushAndSend();
    }
} 