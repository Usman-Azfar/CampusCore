package com.cms.controllers;

import com.cms.dao.UserDAO;
import com.cms.models.Profile;
import com.cms.models.User;
import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

/**
 * Reads and validates account/profile forms: the admin's add/edit form for a
 * student or teacher, and a user's own profile page.
 */
final class UserFormSupport {

    static final int MIN_PASSWORD = 6;

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,50}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern PHONE = Pattern.compile("[0-9+()\\- ]{7,20}");
    private static final String TOO_LONG = new String("\u0000too-long");

    private UserFormSupport() {
    }

    /**
     * Admin form: copies it into user/profile and validates. For an edit, pass the
     * loaded user and profile (fields missing from the form keep their values);
     * for an add, pass new objects. Returns a user-facing error, or null.
     */
    static String read(HttpServletRequest request, User user, Profile profile, boolean isAdd, UserDAO userDAO) {
        String fullName = clean(request.getParameter("fullName"));
        String username = clean(request.getParameter("username"));
        String password = request.getParameter("password") == null ? "" : request.getParameter("password");

        if (fullName.isEmpty() || fullName.length() > 100)
            return "Full name is required (up to 100 characters).";
        if (!USERNAME.matcher(username).matches())
            return "Username must be 3-50 characters: letters, digits, dot, dash or underscore (no spaces).";
        if (isAdd && password.isEmpty())
            return "A password is required for a new account.";
        if (!password.isEmpty() && password.length() < MIN_PASSWORD)
            return "Password must be at least " + MIN_PASSWORD + " characters.";

        Integer exceptId = isAdd ? null : user.getUserId();
        User sameName = userDAO.getUserByUsername(username);
        if (sameName != null && (exceptId == null || sameName.getUserId() != exceptId))
            return "Username " + username + " is already taken.";

        String error = readContact(request, profile, userDAO, exceptId);
        if (error != null)
            return error;
        String fatherName = optional(request, "fatherName", 100);
        if (fatherName == TOO_LONG)
            return "Father name can be at most 100 characters.";
        if (request.getParameter("fatherName") != null)
            profile.setFatherName(fatherName);

        user.setUsername(username);
        user.setPassword(password.isEmpty() ? null : password); // null = keep current (edit)
        profile.setFullName(fullName);
        return null;
    }

    /**
     * Email (required, valid, unique) plus the optional contact fields: gender,
     * phone, city, country, address. Only fields present in the form are changed.
     * Returns a user-facing error, or null.
     */
    static String readContact(HttpServletRequest request, Profile profile, UserDAO userDAO, Integer exceptUserId) {
        String email = clean(request.getParameter("email")).toLowerCase();
        if (email.length() > 100 || !EMAIL.matcher(email).matches())
            return "Please enter a valid email address.";
        if (userDAO.emailInUse(email, exceptUserId))
            return "Email " + email + " is already used by another account.";

        String gender = request.getParameter("gender");
        if (gender != null) {
            gender = gender.trim();
            if (!gender.isEmpty() && !gender.equals("Male") && !gender.equals("Female") && !gender.equals("Other"))
                return "Invalid gender.";
        }
        String phone = request.getParameter("phone");
        if (phone != null) {
            phone = clean(phone);
            if (!phone.isEmpty() && !PHONE.matcher(phone).matches())
                return "Phone number may contain digits, spaces, + ( ) and - (7-20 characters).";
        }
        String city = optional(request, "city", 50);
        String country = optional(request, "country", 50);
        String address = optional(request, "address", 500);
        if (city == TOO_LONG || country == TOO_LONG)
            return "City and country can be at most 50 characters.";
        if (address == TOO_LONG)
            return "Address can be at most 500 characters.";

        // All valid: apply
        profile.setEmail(email);
        if (gender != null) profile.setGender(gender.isEmpty() ? null : gender);
        if (phone != null) profile.setPhone(phone.isEmpty() ? null : phone);
        if (request.getParameter("city") != null) profile.setCity(city);
        if (request.getParameter("country") != null) profile.setCountry(country);
        if (request.getParameter("address") != null) profile.setAddress(address);
        return null;
    }

    // The submitted form fields (except passwords), to refill the form after an error
    static java.util.Map<String, String> draft(HttpServletRequest request) {
        java.util.Map<String, String> d = new java.util.HashMap<>();
        for (String f : new String[] { "fullName", "fatherName", "username", "email", "gender", "phone", "city",
                "country", "address", "isActive", "departmentId", "classId" }) {
            if (request.getParameter(f) != null)
                d.put(f, request.getParameter(f));
        }
        return d;
    }

    // Trimmed value, null when blank or not sent, TOO_LONG when longer than max
    private static String optional(HttpServletRequest request, String name, int max) {
        String v = request.getParameter(name);
        if (v == null)
            return null;
        v = v.trim();
        if (v.length() > max)
            return TOO_LONG;
        return v.isEmpty() ? null : v;
    }

    static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }
}
