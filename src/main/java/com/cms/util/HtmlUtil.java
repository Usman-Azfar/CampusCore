package com.cms.util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Helpers for safely rendering user-supplied values inside JSP pages.
 */
public final class HtmlUtil {

    private HtmlUtil() {
    }

    // Escape text for use in HTML element content or quoted attribute values
    public static String esc(Object value) {
        if (value == null)
            return "";
        String s = value.toString();
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '&':
                    sb.append("&amp;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#39;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    // Format a timestamp as e.g. "29 Sep 2026, 05:37 PM"
    public static String formatDate(Date date) {
        if (date == null)
            return "-";
        return new SimpleDateFormat("dd MMM yyyy, hh:mm a").format(date);
    }
}
