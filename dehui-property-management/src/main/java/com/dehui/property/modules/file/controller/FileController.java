package com.dehui.property.modules.file.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FileController {

    @PostMapping("/files/upload")
    public ApiResponse<Void> upload() {
        throw BusinessException.notImplemented("文件上传");
    }
}
