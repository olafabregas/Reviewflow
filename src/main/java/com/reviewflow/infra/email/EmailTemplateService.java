package com.reviewflow.infra.email;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
@RequiredArgsConstructor
public class EmailTemplateService {

  private final SpringTemplateEngine templateEngine;

  public String renderHtml(String templateName, Map<String, Object> variables) {
    Context context = new Context();
    context.setVariables(variables);
    return templateEngine.process("email/" + templateName, context);
  }

  public String renderText(String templateName, Map<String, Object> variables) {
    Context context = new Context();
    context.setVariables(variables);
    return templateEngine.process("email/text/" + templateName, context);
  }
}
