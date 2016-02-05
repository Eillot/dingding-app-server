package com.mocoder.dingding.service.impl;

import com.mocoder.dingding.constants.SessionKeyConstant;
import com.mocoder.dingding.dao.LoginAccountMapper;
import com.mocoder.dingding.enums.ErrorTypeEnum;
import com.mocoder.dingding.service.LoginAccountService;
import com.mocoder.dingding.model.LoginAccount;
import com.mocoder.dingding.model.LoginAccountCriteria;
import com.mocoder.dingding.rpc.SmsServiceWrap;
import com.mocoder.dingding.utils.bean.RedisRequestSession;
import com.mocoder.dingding.vo.CommonResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Random;

/**
 * Created by yangshuai3 on 2016/1/26.
 */
@Service
public class LoginAccountServiceImpl implements LoginAccountService{

    private static final Logger log = LogManager.getLogger(LoginAccountServiceImpl.class);

    @Resource
    private SmsServiceWrap smsServiceWrap;

    @SuppressWarnings("SpringJavaAutowiringInspection")
    @Resource
    private LoginAccountMapper loginAccountMapper;

    @Override
    public CommonResponse<LoginAccount> loginByPass(String mobile, String password, RedisRequestSession session) {

        CommonResponse<LoginAccount> response = new CommonResponse<LoginAccount>();
        if(session.getAttribute(SessionKeyConstant.USER_LOGIN_KEY,LoginAccount.class)!=null){
            response.resolveErrorInfo(ErrorTypeEnum.LOGIN_DUPLICATE_ERROR);
            return response;
        }
        LoginAccount record = queryLoginAccounts(mobile);
        if(record!=null){
            try {
                if(DigestUtils.md5DigestAsHex(password.getBytes("utf-8")).equals(record.getPassword())){
                    record.setPassword(null);
                    session.setAttribute(SessionKeyConstant.USER_LOGIN_KEY,record);
                    session.setAttribute(SessionKeyConstant.VERIFY_CODE_KEY,null);
                    response.setData(record);
                    response.setCode(0);
                    response.setMsg("成功");
                    response.setUiMsg("登录成功");
                    return response;
                }else{
                    response.resolveErrorInfo(ErrorTypeEnum.PASS_LOGIN_ERROR);
                    return response;
                }
            } catch (UnsupportedEncodingException e) {
                log.error("密码登录-异常：不支持的编码格式 utf-8",e);
                response.resolveErrorInfo(ErrorTypeEnum.SYSTEM_ENV_EXCEPTION);
                return response;
            }
        }else{
            response.resolveErrorInfo(ErrorTypeEnum.ACCOUNT_NOT_EXISTS);
            return response;
        }
    }

    private LoginAccount queryLoginAccounts(String mobile) {
        LoginAccountCriteria example = new LoginAccountCriteria();
        example.createCriteria().andMobileEqualTo(mobile);
        List<LoginAccount> list = loginAccountMapper.selectByExample(example);
        if(!list.isEmpty()){
            return list.get(0);
        }
        return null;
    }

    @Override
    public CommonResponse<LoginAccount> loginByVerifyCode(String mobile, String verifyCode, RedisRequestSession session) {
        CommonResponse<LoginAccount> response = new CommonResponse<LoginAccount>();
        if(session.getAttribute(SessionKeyConstant.USER_LOGIN_KEY,LoginAccount.class)!=null){
            response.resolveErrorInfo(ErrorTypeEnum.LOGIN_DUPLICATE_ERROR);
            return response;
        }
        String code = session.getAttribute(SessionKeyConstant.VERIFY_CODE_KEY, String.class);
        if(verifyCode!=null&&verifyCode.equals(code)){
            LoginAccount record = queryLoginAccounts(mobile);
            if(record!=null){
                record.setPassword(null);
                response.setData(record);
                response.setCode(0);
                response.setMsg("成功");
                response.setUiMsg("登录成功");
                session.setAttribute(SessionKeyConstant.USER_LOGIN_KEY,record);
                session.setAttribute(SessionKeyConstant.VERIFY_CODE_KEY,null);
                return response;
            }else{
                response.resolveErrorInfo(ErrorTypeEnum.ACCOUNT_NOT_EXISTS);
                return response;
            }
        }else{
            response.resolveErrorInfo(ErrorTypeEnum.VALIDATE_CODE_ERROR);
            return response;
        }
    }

    @Override
    public CommonResponse<LoginAccount> registerAccount(LoginAccount account, RedisRequestSession session) {
        CommonResponse<LoginAccount> response = new CommonResponse<LoginAccount>();
        if(session.getAttribute(SessionKeyConstant.USER_LOGIN_KEY,LoginAccount.class)!=null){
            response.resolveErrorInfo(ErrorTypeEnum.NOT_LOG_OUT_ERROR);
            return response;
        }
        LoginAccount record = queryLoginAccounts(account.getMobile());
        if(record!=null){
            response.resolveErrorInfo(ErrorTypeEnum.REG_DUPLICATE_ERROR);
            return  response;
        }
        try {
            account.setPassword(DigestUtils.md5DigestAsHex(account.getPassword().getBytes("utf-8")));
        } catch (UnsupportedEncodingException e) {
            log.error("密码登录-异常：不支持的编码格式 utf-8",e);
            response.resolveErrorInfo(ErrorTypeEnum.SYSTEM_ENV_EXCEPTION);
            return response;
        }
        loginAccountMapper.insertSelective(account);
        account.setPassword(null);
        session.setAttribute(SessionKeyConstant.USER_LOGIN_KEY,account);
        session.setAttribute(SessionKeyConstant.VERIFY_CODE_KEY,null);
        response.setData(account);
        response.setCode(0);
        response.setMsg("注册成功");
        response.setUiMsg("注册成功");
        return response;
    }

    @Override
    public CommonResponse<String> getRegVerifyCode(String mobile, RedisRequestSession session) {
        CommonResponse<String> response = new CommonResponse<String>();
        if(session.getAttribute(SessionKeyConstant.USER_LOGIN_KEY,LoginAccount.class)!=null){
            response.resolveErrorInfo(ErrorTypeEnum.NOT_LOG_OUT_ERROR);
            return response;
        }
        LoginAccount record = queryLoginAccounts(mobile);
        if(record!=null){
            response.resolveErrorInfo(ErrorTypeEnum.REG_DUPLICATE_ERROR);
            return  response;
        }
        Random random = new Random();
        String code = String.valueOf(1000 + random.nextInt(8999));
        session.setAttribute(SessionKeyConstant.VERIFY_CODE_KEY, code);
        if(smsServiceWrap.sentRegValidCodeSms(mobile,code)){
            response.setCode(0);
            return response;
        }else{
            response.resolveErrorInfo(ErrorTypeEnum.SMS_SEND_ERROR);
            return response;
        }
    }

    @Override
    public CommonResponse<String> getLoginVerifyCode(String mobile, RedisRequestSession session) {
        CommonResponse<String> response = new CommonResponse<String>();
        if(session.getAttribute(SessionKeyConstant.USER_LOGIN_KEY,LoginAccount.class)!=null){
            response.resolveErrorInfo(ErrorTypeEnum.LOGIN_DUPLICATE_ERROR);
            return response;
        }
        LoginAccount record = queryLoginAccounts(mobile);
        if(record!=null){
            Random random = new Random();
            String code = String.valueOf(1000 + random.nextInt(8999));
            session.setAttribute(SessionKeyConstant.VERIFY_CODE_KEY, code);
            if(smsServiceWrap.sentRegValidCodeSms(mobile,code)){
                response.setCode(0);
                return response;
            }else{
                response.resolveErrorInfo(ErrorTypeEnum.SMS_SEND_ERROR);
                return response;
            }
        }else{
            response.resolveErrorInfo(ErrorTypeEnum.ACCOUNT_NOT_EXISTS);
            return response;
        }
    }
}