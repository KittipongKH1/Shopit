package com.ecom.controller;

import com.ecom.model.UserDtls;
import com.ecom.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController                          // ✅ ส่งข้อมูลออกมาเป็น JSON
@RequestMapping("/api/users")            // ✅ endpoint หลัก
public class UserRestController {

    @Autowired
    private UserService userService;

    // ✅ GET ผู้ใช้ทั้งหมด
    @GetMapping
    public List<UserDtls> getAllUsers() {
        return userService.getAllUsers();
    }

    // ✅ GET ผู้ใช้ตาม id
    @GetMapping("/{id}")
    public UserDtls getUserById(@PathVariable Integer id) {
        return userService.getUserById(id);
    }

    // ✅ GET ผู้ใช้ตาม email
    @GetMapping("/by-email")
    public UserDtls getUserByEmail(@RequestParam String email) {
        return userService.getUserByEmail(email);
    }
}
