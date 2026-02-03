/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.mapper;

import com.alibaba.cloud.ai.dataagent.entity.FileStorage;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FileStorageMapper {

	@Select("""
			SELECT * FROM file_storage ORDER BY created_time DESC
			""")
	List<FileStorage> findAll();

	@Select("""
			SELECT * FROM file_storage WHERE id = #{id}
			""")
	FileStorage findById(Long id);

	@Select("""
			SELECT * FROM file_storage WHERE file_path = #{filePath}
			""")
	FileStorage findByFilePath(String filePath);

	@Insert("""
			INSERT INTO file_storage (filename, file_path, file_size, file_type, file_extension, storage_type, is_deleted, is_cleaned, created_time, updated_time)
			VALUES (#{filename}, #{filePath}, #{fileSize}, #{fileType}, #{fileExtension}, #{storageType}, #{isDeleted}, #{isCleaned}, #{createdTime}, #{updatedTime})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(FileStorage fileStorage);

	@Update("""
			<script>
			UPDATE file_storage
			<set>
				<if test="storageType != null">storage_type = #{storageType},</if>
				<if test="isDeleted != null">is_deleted = #{isDeleted},</if>
				<if test="isCleaned != null">is_cleaned = #{isCleaned},</if>
				<if test="updatedTime != null"> updated_time = #{updatedTime},</if>
			</set>
			WHERE id = #{id}
			</script>
			""")
	int update(FileStorage fileStorage);

	@Delete("""
			DELETE FROM file_storage WHERE id = #{id}
			""")
	int deleteById(Long id);

	@Select("""
			    SELECT * FROM file_storage
			    WHERE is_deleted = 1
			      AND is_cleaned = 0
			      AND updated_time < #{beforeTime}
			    LIMIT #{limit}
			""")
	List<FileStorage> selectDirtyRecords(LocalDateTime beforeTime, int limit);

}
