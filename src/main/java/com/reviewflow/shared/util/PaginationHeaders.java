package com.reviewflow.shared.util;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;

public final class PaginationHeaders {

  private PaginationHeaders() {}

  public static HttpHeaders forPage(Page<?> page) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Total-Elements", String.valueOf(page.getTotalElements()));
    headers.add("X-Total-Pages", String.valueOf(page.getTotalPages()));
    return headers;
  }
}
