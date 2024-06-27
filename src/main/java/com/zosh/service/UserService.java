package com.zosh.service;

import org.springframework.stereotype.Service;

import com.zosh.exception.UserException;

import com.zosh.model.User;

public interface UserService {
	
	public User findUserById(Long userId) throws UserException;
	
	public User findUserProfileByJwt(String jwt) throws UserException;
	
	
	
}
