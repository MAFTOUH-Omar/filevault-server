package com.filevault.filevaultserver.action.auth;

import com.filevault.filevaultserver.repository.user.UserRepository;
import com.filevault.filevaultserver.response.user.UserSummaryResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MeAction {

    private final UserRepository userRepository;

    public MeAction(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse execute(UUID userId) {
        return UserSummaryResponse.from(userRepository.getOrThrow(userId));
    }
}
