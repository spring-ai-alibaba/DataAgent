/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.service.impl;

import com.alibaba.cloud.ai.config.FileUploadProperties;
import com.alibaba.cloud.ai.service.FileStorageService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;


@Slf4j
@Service
@AllArgsConstructor
public class LocalFileStorageServiceImpl implements FileStorageService {

    private final FileUploadProperties fileUploadProperties;

    @Override
    public String storeFile(MultipartFile file, String subPath) {
        try {
            // 创建上传目录
            Path uploadDir = Paths.get(fileUploadProperties.getPath(), subPath);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // 生成文件名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = UUID.randomUUID().toString() + extension;

            // 存储文件
            Path filePath = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), filePath);

            // 返回相对路径
            return subPath + "/" + filename;

        } catch (IOException e) {
            log.error("文件存储失败", e);
            throw new RuntimeException("文件存储失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteFile(String filePath) {
        try {
            Path fullPath = Paths.get(fileUploadProperties.getPath(), filePath);
            if (Files.exists(fullPath)) {
                Files.delete(fullPath);
                log.info("成功删除文件: {}", filePath);
                return true;
            } else {
                log.warn("文件不存在，无法删除: {}", filePath);
                return false;
            }
        } catch (IOException e) {
            log.error("删除文件失败: {}", filePath, e);
            return false;
        }
    }

    @Override
    public String getFileUrl(String filePath) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return ServletUriComponentsBuilder.fromRequestUri(request)
                        .replacePath(fileUploadProperties.getUrlPrefix() + "/" + filePath)
                        .build()
                        .toUriString();
            }
        } catch (Exception e) {
            log.warn("Failed to build dynamic URL, fallback to relative path", e);
        }
        return fileUploadProperties.getUrlPrefix() + "/" + filePath;
    }

}
