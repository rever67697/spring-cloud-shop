package quick.pager.shop.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import quick.pager.shop.user.client.AuthClient;
import quick.pager.shop.user.client.UserClient;
import quick.pager.shop.configuration.ShopRedisTemplate;
import quick.pager.shop.dto.UserDTO;
import quick.pager.shop.exception.OAuth2SmsInvalidException;
import quick.pager.shop.user.response.Response;

/**
 * 查询用户权限
 *
 * @author siguiyang
 * @version 3.0
 */
@Service
public class UserServiceImpl implements UserDetailsService {

    @Autowired
    private AuthClient authClient;
    @Autowired
    private UserClient userClient;
    @Autowired
    private ShopRedisTemplate shopRedisTemplate;
    /**
     * 短信登陆redis key
     */
    private static final String REDIS_SMS_PREFIX = "sms:login:code";

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        Response<UserDTO> sysUserResponse = authClient.getSysUser(username);
        if (null == sysUserResponse || null == sysUserResponse.getData()) {
            throw new UsernameNotFoundException("用户不存在");
        }

        UserDTO sysUser = sysUserResponse.getData();

        Collection<? extends GrantedAuthority> grantedAuthorities = this.getGrantedAuthority(sysUser.getId());

        return new User(username, sysUser.getPassword(), grantedAuthorities);
    }

    /**
     * 根据手机号码查询人
     *
     * @param phone   手机号码
     * @param smsCode 短信验证码
     * @return 授权人
     * @throws UsernameNotFoundException 未找到用户
     */
    public Collection<? extends GrantedAuthority> loadUserBySMS(String phone, String smsCode) throws UsernameNotFoundException {

        // 是否输入短信验证码
        if (StringUtils.isEmpty(smsCode)) {
            throw new OAuth2SmsInvalidException("请输入短信验证码");
        }
        String redisSmsCode = (String) shopRedisTemplate.opsForValue().get(REDIS_SMS_PREFIX + phone);

        // redis 中短信验证码是否存在，过期
        if (StringUtils.isEmpty(redisSmsCode)) {
            throw new OAuth2SmsInvalidException("请先获取短信验证码");
        }

        // 验证短信验证码是否正确
        if (!smsCode.equals(redisSmsCode)) {
            throw new OAuth2SmsInvalidException("短信验证码不正确");
        }

        Response<UserDTO> sysUserResponse = authClient.getSysUser(phone);
        if (null == sysUserResponse || null == sysUserResponse.getData()) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return getGrantedAuthority(sysUserResponse.getData().getId());
    }


    /**
     * 获取权限
     *
     * @param sysUserId 系统用户主键
     * @return 权限列表
     */
    private Collection<? extends GrantedAuthority> getGrantedAuthority(final Long sysUserId) {
        List<SimpleGrantedAuthority> grantedAuthorities = new ArrayList<>();
        Response<List<String>> roleResponse = authClient.getRolesBySysUserId(sysUserId);

        if (200 == roleResponse.getCode()) {
            grantedAuthorities = Optional.ofNullable(roleResponse.getData()).orElse(Collections.emptyList()).stream()
                    .map(SimpleGrantedAuthority::new).collect(Collectors.toList());
        }

        return grantedAuthorities;
    }

}
