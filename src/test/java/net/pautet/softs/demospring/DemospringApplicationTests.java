package net.pautet.softs.demospring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;

@SpringBootTest
class DemospringApplicationTests {

	@MockitoBean(answers = RETURNS_DEEP_STUBS)
	private StringRedisTemplate redisTemplate;

	@Test
	void contextLoads() {
	}

}
