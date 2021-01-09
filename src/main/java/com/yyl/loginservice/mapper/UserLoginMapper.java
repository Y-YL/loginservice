package com.yyl.loginservice.mapper;


import com.yyl.loginservice.bean.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserLoginMapper {

    User selectById(int id);

    User selectByName(String username);

    User selectByEmail(String email);

    int insertUser(User user);

    int updateStatus(@Param("id") int id, @Param("status") int status);

    int updateHeader(@Param("id") int id, @Param("headerUrl") String headerUrl);

    int updatePassword(@Param("id") int id, @Param("password") String password, @Param("salt") String salt);

}
