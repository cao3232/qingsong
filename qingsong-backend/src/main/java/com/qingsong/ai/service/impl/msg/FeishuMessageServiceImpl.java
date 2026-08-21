package com.qingsong.ai.service.impl.msg;

import com.alibaba.fastjson.JSON;
import com.qingsong.ai.entity.dto.FeishuCardDTO;
import com.qingsong.ai.entity.dto.FeishuMsgReqDTO;
import com.qingsong.ai.entity.vo.FeishuMsgRespVO;
import com.qingsong.ai.service.msg.FeishuMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class FeishuMessageServiceImpl implements FeishuMessageService {

    @Value("${feishu.secret}")
    private String secret;

    @Value("${feishu.url}")
    private String url;

    private final RestTemplate restTemplate;

    @Override
    public FeishuMsgRespVO sendSingleMessage(String message) {
        FeishuMsgReqDTO feishuMsgReqDTO = this.buildFeishuMsgReqDTO(message, null);
        return invokeSendFeishuMsg(feishuMsgReqDTO);
    }

    @Override
    public FeishuMsgRespVO sendUserMessage(String message, String user) {
        FeishuMsgReqDTO feishuMsgReqDTO = this.buildFeishuMsgReqDTO(message, user);
        return invokeSendFeishuMsg(feishuMsgReqDTO);
    }

    @Override
    public FeishuMsgRespVO sendUserMdMessage(String message, String user) {
        FeishuCardDTO feishuCardDTO = buildFeishuMsgMdReqDTO(message, user);
        ResponseEntity<FeishuMsgRespVO> feishuMsgRespVOResponseEntity = restTemplate.postForEntity(url, feishuCardDTO, FeishuMsgRespVO.class);

        if (feishuMsgRespVOResponseEntity.getStatusCode().is2xxSuccessful()) {
            log.info("发送飞书请求成功");
            if ("success".equals(feishuMsgRespVOResponseEntity.getBody().getMsg())) {
                log.info("发送飞书消息成功");
                return feishuMsgRespVOResponseEntity.getBody();
            } else {
                log.info("发送飞书消息失败");
                throw new RuntimeException("发送飞书消息失败" + feishuMsgRespVOResponseEntity.getBody().getMsg());
            }
        }
        return null;
    }

    @Override
    public Boolean sendChatMsg(String role, String chatId) {
        return true;
    }

    private FeishuMsgReqDTO buildFeishuMsgReqDTO(String message, String user) {
        FeishuMsgReqDTO feishuMsgReqDTO = new FeishuMsgReqDTO();
        feishuMsgReqDTO.setMsg_type("text");
        feishuMsgReqDTO.setTimestamp(String.valueOf(System.currentTimeMillis() / 1000));
        try {
            feishuMsgReqDTO.setSign(this.genSign(secret, feishuMsgReqDTO.getTimestamp()));
        } catch (Exception e) {
            log.error("发送飞书消息异常,生成签名失败", e);
            throw new RuntimeException(e);
        }
        FeishuMsgReqDTO.Content content = new FeishuMsgReqDTO.Content();
        if (user != null) {
            String userStr = "<at user_id=\"" + user + "\"></at>";
            message = userStr + message;
        }
        content.setText(message);
        feishuMsgReqDTO.setContent(content);
        return feishuMsgReqDTO;
    }

    private FeishuMsgRespVO invokeSendFeishuMsg(FeishuMsgReqDTO message) {
        log.info("发送飞书消息开始" + JSON.toJSON(message));
        ResponseEntity<FeishuMsgRespVO> feishuMsgRespVOResponseEntity = restTemplate.postForEntity(url, message, FeishuMsgRespVO.class);

        if (feishuMsgRespVOResponseEntity.getStatusCode().is2xxSuccessful()) {
            log.info("发送飞书请求成功");
            if ("success".equals(feishuMsgRespVOResponseEntity.getBody().getMsg())) {
                log.info("发送飞书消息成功");
                return feishuMsgRespVOResponseEntity.getBody();
            } else {
                log.info("发送飞书消息失败");
                throw new RuntimeException("发送飞书消息失败" + feishuMsgRespVOResponseEntity.getBody().getMsg());
            }
        }
        return null;
    }

    private FeishuCardDTO buildFeishuMsgMdReqDTO(String message, String user) {
        FeishuCardDTO cardDTO = new FeishuCardDTO();
        try {
            cardDTO.setTimestamp(String.valueOf(System.currentTimeMillis() / 1000));
            cardDTO.setSign(genSign(secret, String.valueOf(cardDTO.getTimestamp())));
        } catch (Exception e) {
            log.error("发送飞书消息异常,生成签名失败:{}", e.getMessage(), e);
        }

        FeishuCardDTO.Card card = new FeishuCardDTO.Card();
        cardDTO.setCard(card);

        FeishuCardDTO.Body body = new FeishuCardDTO.Body();
        card.setBody(body);

        List<FeishuCardDTO.Element> elements = new ArrayList<>();

        FeishuCardDTO.Element markdownElement = new FeishuCardDTO.Element();
        markdownElement.setTag("markdown");
        markdownElement.setContent(message);
        elements.add(markdownElement);

        body.setElements(elements);

        return cardDTO;
    }

    private static String genSign(String secret, String timestamp) throws NoSuchAlgorithmException, InvalidKeyException {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(new byte[]{});
        return new String(Base64.getEncoder().encode(signData));
    }
}
