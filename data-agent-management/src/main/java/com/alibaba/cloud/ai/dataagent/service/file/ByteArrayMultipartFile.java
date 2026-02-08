package com.alibaba.cloud.ai.dataagent.service.file;

import lombok.AllArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * 简单的 MultipartFile 实现，用于将字节数组包装为 MultipartFile
 */
@AllArgsConstructor
public class ByteArrayMultipartFile implements MultipartFile {

	private final byte[] content;

	private final String filename;

	private final String contentType;

	@Override
	public String getName() {
		return "file";
	}

	@Override
	public String getOriginalFilename() {
		return filename;
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public boolean isEmpty() {
		return content == null || content.length == 0;
	}

	@Override
	public long getSize() {
		return content.length;
	}

	@Override
	public byte[] getBytes() {
		return content;
	}

	@Override
	public InputStream getInputStream() {
		return new ByteArrayInputStream(content);
	}

	@Override
	public void transferTo(File dest) throws IOException, IllegalStateException {
		throw new UnsupportedOperationException("transferTo is not supported");
	}

}
