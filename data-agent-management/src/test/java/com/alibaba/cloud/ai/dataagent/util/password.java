package com.alibaba.cloud.ai.dataagent.util;

import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class password {


        public static void main(String[] args) {
            String hash = "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi";
            System.out.println("验证结果: " + BCrypt.checkpw("admin123", hash));
            System.out.println("新哈希值: " + BCrypt.hashpw("admin123", BCrypt.gensalt()));

    }

}
