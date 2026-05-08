package com.datalabeling.dto.mapper;

import com.datalabeling.dto.response.UserVO;
import com.datalabeling.entity.User;
import org.springframework.stereotype.Component;

/**
 * 用户DTO转换器
 */
@Component
public class UserMapper {

    /**
     * Entity转VO
     */
    public UserVO toVO(User user) {
        if (user == null) {
            return null;
        }

        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRole(user.getRole());
        vo.setEmail(user.getEmail());
        vo.setFullName(user.getFullName());
        vo.setIsActive(user.getIsActive());
        vo.setLastLogin(user.getLastLogin());
        vo.setCreatedAt(user.getCreatedAt());

        return vo;
    }
}
