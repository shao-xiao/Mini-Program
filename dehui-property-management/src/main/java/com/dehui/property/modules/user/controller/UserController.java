package com.dehui.property.modules.user.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.user.entity.User;
import com.dehui.property.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/list")
    public Result<List<User>> list() {
        return Result.success(userService.findAll());
    }

    @PostMapping("/save")
    public Result<User> save(@RequestBody User user) {
        return Result.success(userService.save(user));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.deleteById(id);
        return Result.success();
    }
}
