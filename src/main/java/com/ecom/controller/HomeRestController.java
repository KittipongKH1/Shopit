package com.ecom.controller;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.model.Category;
import com.ecom.model.Product;
import com.ecom.model.UserDtls;
import com.ecom.service.CartService;
import com.ecom.service.CategoryService;
import com.ecom.service.ProductService;
import com.ecom.service.UserService;
import com.ecom.util.CommonUtil;

import io.micrometer.common.util.StringUtils;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class HomeRestController {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductService productService;

    @Autowired
    private UserService userService;

    @Autowired
    private CommonUtil commonUtil;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private CartService cartService;

    // ✅ ดึงข้อมูล User ที่ล็อกอิน
    @GetMapping("/user/details")
    public ResponseEntity<?> getUserDetails(Principal p) {
        if (p == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        }
        UserDtls user = userService.getUserByEmail(p.getName());
        int countCart = cartService.getCountCart(user.getId());
        return ResponseEntity.ok(Map.of(
                "user", user,
                "countCart", countCart
        ));
    }

    // ✅ หน้าแรก: แสดงหมวดหมู่และสินค้าใหม่
    @GetMapping("/home")
    public ResponseEntity<?> home() {
        List<Category> allActiveCategory = categoryService.getAllActiveCategory().stream()
                .sorted((c1, c2) -> c2.getId().compareTo(c1.getId()))
                .limit(6)
                .toList();

        List<Product> allActiveProducts = productService.getAllActiveProducts("").stream()
                .sorted((p1, p2) -> p2.getId().compareTo(p1.getId()))
                .limit(8)
                .toList();

        return ResponseEntity.ok(Map.of(
                "categories", allActiveCategory,
                "products", allActiveProducts
        ));
    }

    // ✅ หมวดหมู่ทั้งหมด
    @GetMapping("/categories")
    public ResponseEntity<List<Category>> getCategories() {
        return ResponseEntity.ok(categoryService.getAllActiveCategory());
    }

    // ✅ สินค้าพร้อม pagination และ search
    @GetMapping("/products")
    public ResponseEntity<?> getProducts(
            @RequestParam(value = "category", defaultValue = "") String category,
            @RequestParam(value = "pageNo", defaultValue = "0") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "12") Integer pageSize,
            @RequestParam(defaultValue = "") String ch) {

        Page<Product> page;
        if (StringUtils.isEmpty(ch)) {
            page = productService.getAllActiveProductPagination(pageNo, pageSize, category);
        } else {
            page = productService.searchActiveProductPagination(pageNo, pageSize, category, ch);
        }

        return ResponseEntity.ok(Map.of(
                "products", page.getContent(),
                "pageNo", page.getNumber(),
                "pageSize", page.getSize(),
                "totalElements", page.getTotalElements(),
                "totalPages", page.getTotalPages(),
                "isFirst", page.isFirst(),
                "isLast", page.isLast()
        ));
    }

    // ✅ รายละเอียดสินค้า
    @GetMapping("/product/{id}")
    public ResponseEntity<?> getProduct(@PathVariable int id) {
        Product product = productService.getProductById(id);
        return product != null
                ? ResponseEntity.ok(product)
                : ResponseEntity.status(404).body(Map.of("error", "Product not found"));
    }

    // ✅ ลงทะเบียนผู้ใช้
    @PostMapping("/users")
    public ResponseEntity<?> saveUser(
            @RequestPart("user") UserDtls user,
            @RequestPart(value = "file", required = false) MultipartFile file) throws IOException {

        if (userService.existsEmail(user.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already exists"));
        }

        String imageName = (file == null || file.isEmpty()) ? "default.jpg" : file.getOriginalFilename();
        user.setProfileImage(imageName);
        UserDtls saved = userService.saveUser(user);

        if (saved != null && file != null && !file.isEmpty()) {
            File saveFile = new ClassPathResource("static/img").getFile();
            Path path = Paths.get(saveFile.getAbsolutePath() + File.separator + "profile_img"
                    + File.separator + file.getOriginalFilename());
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
        }

        return ResponseEntity.ok(Map.of("message", "Register successfully", "user", saved));
    }

    // ✅ ค้นหาสินค้า
    @GetMapping("/search")
    public ResponseEntity<List<Product>> searchProducts(@RequestParam String ch) {
        return ResponseEntity.ok(productService.searchProduct(ch));
    }

    // ✅ Forgot password - ขอ reset link
    @PostMapping("/forgot-password")
    public ResponseEntity<?> processForgotPassword(
            @RequestParam String email,
            HttpServletRequest request) throws UnsupportedEncodingException, MessagingException {

        UserDtls userByEmail = userService.getUserByEmail(email);

        if (ObjectUtils.isEmpty(userByEmail)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid email"));
        }

        String resetToken = UUID.randomUUID().toString();
        userService.updateUserResetToken(email, resetToken);

        String url = CommonUtil.generateUrl(request) + "/reset-password?token=" + resetToken;
        Boolean sendMail = commonUtil.sendMail(url, email);

        if (sendMail) {
            return ResponseEntity.ok(Map.of("message", "Password reset link sent to email"));
        } else {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to send email"));
        }
    }

    // ✅ Reset password
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @RequestParam String token,
            @RequestParam String password) {

        UserDtls userByToken = userService.getUserByToken(token);
        if (userByToken == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Link invalid or expired"));
        }

        userByToken.setPassword(passwordEncoder.encode(password));
        userByToken.setResetToken(null);
        userService.updateUser(userByToken);

        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
