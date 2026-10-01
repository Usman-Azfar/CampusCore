package com.cms.dao;

import com.cms.models.Profile;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ProfileDAO {

    public Profile getProfileByUserId(int userId) {
        Profile profile = null;
        String sql = "SELECT * FROM profiles WHERE user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    profile = new Profile();
                    profile.setProfileId(rs.getInt("profile_id"));
                    profile.setUserId(rs.getInt("user_id"));
                    profile.setFullName(rs.getString("full_name"));
                    profile.setGender(rs.getString("gender"));
                    profile.setFatherName(rs.getString("father_name"));
                    profile.setPhone(rs.getString("phone"));
                    profile.setAddress(rs.getString("address"));
                    profile.setCity(rs.getString("city"));
                    profile.setCountry(rs.getString("country"));
                    profile.setEmail(rs.getString("email"));
                    profile.setImgData(rs.getBytes("img_data"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return profile;
    }

    public boolean updateProfile(Profile profile) {
        String sql = "UPDATE profiles SET full_name=?, gender=?, father_name=?, phone=?, address=?, city=?, country=?, email=? WHERE user_id=?";
        
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, profile.getFullName());
            stmt.setString(2, profile.getGender());
            stmt.setString(3, profile.getFatherName());
            stmt.setString(4, profile.getPhone());
            stmt.setString(5, profile.getAddress());
            stmt.setString(6, profile.getCity());
            stmt.setString(7, profile.getCountry());
            stmt.setString(8, profile.getEmail());
            stmt.setInt(9, profile.getUserId());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}
