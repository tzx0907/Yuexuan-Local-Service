package com.sky.service;

import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.result.Result;

public interface UserService {
    User wxLogin(UserLoginDTO loginDTO);
}
