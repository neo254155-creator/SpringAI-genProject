package com.first.genProject.persistence;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties", properties = "spring.datasource.url=jdbc:h2:mem:history-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class InterviewPersistenceTest extends InterviewPersistenceContract {
}
