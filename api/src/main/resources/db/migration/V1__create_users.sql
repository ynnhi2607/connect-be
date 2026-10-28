CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `auth_provider` enum('GOOGLE','LOCAL') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(160) NOT NULL,
  `name` varchar(80) NOT NULL,
  `password_hash` varchar(255) DEFAULT NULL,
  `provider_id` varchar(160) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `uk_users_provider_provider_id` (`auth_provider`,`provider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;