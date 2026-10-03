package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.PasswordConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.EmployeeMapper;
import com.sky.result.PageResult;
import com.sky.service.EmployeeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            //账号不存在
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        //密码比对：BCrypt（当前格式）与历史 MD5 存储均兼容
        if (!matchesPassword(password, employee.getPassword())) {
            //密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }
        //历史 MD5 密码登录成功后透明升级为 BCrypt
        upgradeLegacyMd5Password(employee, password);

        if (employee.getStatus() == StatusConstant.DISABLE) {
            //账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //3、返回实体对象
        return employee;
    }
    /**
     *新增员工
     *
     * @param employeeDTO
     * @return
     */
    @Override
    public void save(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);
        employee.setStatus(StatusConstant.ENABLE);
        employee.setPassword(passwordEncoder.encode(PasswordConstant.DEFAULT_PASSWORD));
        employeeMapper.insert(employee);
    }
    /**
     *员工分页查询
     *
     * @param employeePageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        PageHelper.startPage(employeePageQueryDTO.getPage(), employeePageQueryDTO.getPageSize());
        Page<Employee> page = employeeMapper.pageQuery(employeePageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }
    /**
     *员工状态修改
     *
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        Employee employee = Employee.builder()
                .status(status)
                .id(id)
                .build();
        employeeMapper.update(employee);
    }
    /**
     *编辑员工信息
     *
     * @param employeeDTO
     */
    @Override
    public void update(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);
        employeeMapper.update(employee);
    }
    /**
     *根据id查询员工信息
     *
     * @param id
     * @return
     */
    @Override
    public Employee getById(Long id) {
        Employee employee = employeeMapper.getById(id);
        employee.setPassword("****");
        return employee;
    }
    /**
     *根据id修改密码
     *
     * @param employeeEditPasswordDTO
     * @return
     */
    @Override
    public void editPassword(EmployeeEditPasswordDTO employeeEditPasswordDTO) {
        String oldPassword = employeeEditPasswordDTO.getOldPassword();
        String newPassword = employeeEditPasswordDTO.getNewPassword();

        if (oldPassword == null || oldPassword.trim().isEmpty()) {
            throw new PasswordErrorException("旧密码不能为空");
        }
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new PasswordErrorException("新密码不能为空");
        }

        Long empId = BaseContext.getCurrentId();

        Employee employee = employeeMapper.getById(empId);
        if (employee == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        if (!matchesPassword(oldPassword, employee.getPassword())) {
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (matchesPassword(newPassword, employee.getPassword())) {
            throw new PasswordErrorException("新密码不能与旧密码相同");
        }

        if (newPassword.length() < 6 || newPassword.length() > 20) {
            throw new PasswordErrorException("新密码长度必须在6-20位之间");
        }

        Employee updateEmployee = Employee.builder()
                .id(empId)
                .password(passwordEncoder.encode(newPassword))
                .build();

        employeeMapper.update(updateEmployee);
    }

    /** 统一密码比对：BCrypt 存储用 matches，历史 MD5 存储回退为摘要比对。 */
    private boolean matchesPassword(String rawPassword, String storedPassword) {
        if (isBCrypt(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        String md5 = DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8));
        return md5.equalsIgnoreCase(storedPassword);
    }

    private boolean isBCrypt(String storedPassword) {
        return storedPassword != null && storedPassword.startsWith("$2");
    }

    /** 存量数据仍为 MD5 时，登录成功后改写为 BCrypt，逐步完成迁移。 */
    private void upgradeLegacyMd5Password(Employee employee, String rawPassword) {
        if (isBCrypt(employee.getPassword())) {
            return;
        }
        employeeMapper.update(Employee.builder()
                .id(employee.getId())
                .password(passwordEncoder.encode(rawPassword))
                .build());
    }
}
