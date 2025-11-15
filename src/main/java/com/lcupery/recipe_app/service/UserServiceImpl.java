package com.lcupery.recipe_app.service;

import com.lcupery.recipe_app.entity.User;
import com.lcupery.recipe_app.exception.ResourceNotFoundException;
import com.lcupery.recipe_app.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@AllArgsConstructor
public class UserServiceImpl implements UserService {

    private UserRepository userRepository;

    @Override
    @Transactional
    public User findOrCreateUser(String auth0Id, String email, String name) {
        return userRepository.findByAuth0Id(auth0Id)
                .orElseGet(() -> {
                    log.info("Creating new user with auth0_id: {}, email: {}", auth0Id, email);
                    User newUser = new User();
                    newUser.setAuth0Id(auth0Id);
                    newUser.setEmail(email);
                    newUser.setName(name);
                    return userRepository.save(newUser);
                });
    }

    @Override
    public User getUserByAuth0Id(String auth0Id) {
        return userRepository.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with auth0_id: " + auth0Id));
    }
}
