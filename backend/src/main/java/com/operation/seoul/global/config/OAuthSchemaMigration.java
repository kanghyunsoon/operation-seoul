package com.operation.seoul.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuthSchemaMigration implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                create table if not exists user_social_accounts (
                    id bigint not null auto_increment,
                    user_id bigint not null,
                    provider varchar(32) not null,
                    provider_user_id varchar(255) not null,
                    created_at datetime not null default current_timestamp,
                    primary key (id),
                    unique key uk_social_provider_user (provider, provider_user_id),
                    unique key uk_social_user_provider (user_id, provider),
                    constraint fk_social_account_user
                        foreign key (user_id) references users (id)
                        on delete cascade
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
    }
}
