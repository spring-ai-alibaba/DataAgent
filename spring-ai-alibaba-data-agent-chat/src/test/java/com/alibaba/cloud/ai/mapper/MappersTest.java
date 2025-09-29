package com.alibaba.cloud.ai.mapper;

import com.alibaba.cloud.ai.MySqlContainerConfiguration;
import org.junit.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

@MybatisTest
@ImportTestcontainers(MySqlContainerConfiguration.class)
@ImportAutoConfiguration(MySqlContainerConfiguration.class)
public class MappersTest {

	@Test
	public void testContextLoads() {
	}

}
