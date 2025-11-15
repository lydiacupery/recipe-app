package com.lcupery.recipe_app.service;

import com.lcupery.recipe_app.entity.User;

public interface UserService {
    User findOrCreateUser(String auth0Id, String email, String name);
    User getUserByAuth0Id(String auth0Id);
}
