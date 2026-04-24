package com.afternote.domain.deepthought.service;

import com.afternote.domain.deepthought.dto.DeepThoughtHealthResponse;
import org.springframework.stereotype.Service;

@Service
public class DeepThoughtService {

    public DeepThoughtHealthResponse getLayerStatus() {
        return new DeepThoughtHealthResponse("deepthought mvc scaffold ready");
    }
}
