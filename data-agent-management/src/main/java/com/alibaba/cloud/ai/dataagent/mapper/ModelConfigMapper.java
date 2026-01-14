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

import com.alibaba.cloud.ai.dataagent.entity.ModelConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ModelConfigMapper {

	@Select("""
			SELECT id, provider, base_url, api_key, model_name, temperature, is_active, max_tokens, model_type, completions_path, embeddings_path, created_time, updated_time, is_deleted FROM model_config WHERE is_deleted = 0 ORDER BY created_time DESC
			""")
	List<ModelConfig> findAll();

	@Select("""
			SELECT id, provider, base_url, api_key, model_name, temperature, is_active, max_tokens, model_type, completions_path, embeddings_path, created_time, updated_time, is_deleted FROM model_config WHERE id = #{id} AND is_deleted = 0
			""")
	ModelConfig findById(Integer id);

	@Select("SELECT id, provider, base_url, api_key, model_name, temperature, is_active, max_tokens, model_type, completions_path, embeddings_path, created_time, updated_time, is_deleted FROM model_config WHERE model_type = #{modelType} AND is_active = 1 AND is_deleted = 0 LIMIT 1")
	ModelConfig selectActiveByType(@Param("modelType") String modelType);

	@Update("UPDATE model_config SET is_active = 0 WHERE model_type = #{modelType} AND id != #{currentId} AND is_deleted = 0")
	void deactivateOthers(@Param("modelType") String modelType, @Param("currentId") Integer currentId);

	@Select("""
			<script>
				SELECT id, provider, base_url, api_key, model_name, temperature, is_active, max_tokens, model_type, completions_path, embeddings_path, created_time, updated_time, is_deleted FROM model_config
				<where>
					is_deleted = 0
					<if test='provider != null and provider != ""'>
						AND provider = #{provider}
					</if>
					<if test='keyword != null and keyword != ""'>
						AND (provider LIKE CONCAT('%', #{keyword}, '%')
							 OR base_url LIKE CONCAT('%', #{keyword}, '%')
							 OR model_name LIKE CONCAT('%', #{keyword}, '%'))
					</if>
					<if test='isActive != null'>
						AND is_active = #{isActive}
					</if>
					<if test='maxTokens != null'>
						AND max_tokens = #{maxTokens}
					</if>
					<if test='modelType != null'>
						AND model_type = #{modelType}
					</if>
				</where>
				ORDER BY created_time DESC
			</script>
			""")
	List<ModelConfig> findByConditions(@Param("provider") String provider, @Param("keyword") String keyword,
			@Param("isActive") Boolean isActive, @Param("maxTokens") Integer maxTokens,
			@Param("modelType") String modelType);

	@Insert("""
			INSERT INTO model_config (provider, base_url, api_key, model_name, temperature, is_active, max_tokens, model_type, completions_path, embeddings_path, created_time, updated_time, is_deleted)
			VALUES (#{provider}, #{baseUrl}, #{apiKey}, #{modelName}, #{temperature}, #{isActive}, #{maxTokens}, #{modelType}, #{completionsPath}, #{embeddingsPath}, NOW(), NOW(), 0)
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(ModelConfig modelConfig);

	@Update("""
			<script>
			          UPDATE model_config
			          <trim prefix="SET" suffixOverrides=",">
			            <if test='provider != null'>provider = #{provider},</if>
			            <if test='baseUrl != null'>base_url = #{baseUrl},</if>
			            <if test='apiKey != null'>api_key = #{apiKey},</if>
			            <if test='modelName != null'>model_name = #{modelName},</if>
			            <if test='temperature != null'>temperature = #{temperature},</if>
			            <if test='isActive != null'>is_active = #{isActive},</if>
			            <if test='maxTokens != null'>max_tokens = #{maxTokens},</if>
			            <if test='modelType != null'>model_type = #{modelType},</if>
			            <if test='completionsPath != null'>completions_path = #{completionsPath},</if>
			            <if test='embeddingsPath != null'>embeddings_path = #{embeddingsPath},</if>
			            <if test='isDeleted != null'>is_deleted = #{isDeleted},</if>
			            updated_time = NOW()
			          </trim>
			          WHERE id = #{id}
			</script>
			""")
	int updateById(ModelConfig modelConfig);

	@Update("""
			UPDATE model_config SET is_deleted = 1 WHERE id = #{id}
			""")
	int deleteById(Integer id);

}
