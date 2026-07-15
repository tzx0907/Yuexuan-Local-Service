package com.sky.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class EmployeeEditPasswordDTO {
    @ApiModelProperty("旧密码")
    private String oldPassword;
    @ApiModelProperty("新密码")
    private String newPassword;
}
