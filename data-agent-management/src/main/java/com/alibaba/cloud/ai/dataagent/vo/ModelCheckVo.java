package com.alibaba.cloud.ai.dataagent.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ModelCheckVo {

	boolean chatModelReady;

	boolean embeddingModelReady;

	boolean ready;

}
