package com.aeroops.predict;

import com.aeroops.predict.api.DelayResponse;
import com.aeroops.predict.api.PredictionController;
import com.aeroops.predict.persistence.PredictionLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PredictionController.class)
class PredictionControllerTest {
    @Autowired MockMvc mvc;
    @MockBean DelayPredictionService service;
    @MockBean PredictionLogRepository repo;

    @Test
    void returnsSnakeCasePrediction() throws Exception {
        when(service.predict(any())).thenReturn(new DelayResponse(0.61, true, 0.35, "2024-03-31", List.of(), false, "v"));
        mvc.perform(post("/api/v1/predict/delay").contentType(MediaType.APPLICATION_JSON).content("""
                {"carrier":"WN","origin":"ATL","dest":"LGA","flight_date":"2024-04-02","dep_hour":9,"distance":762,"prev_leg_arr_delay":40}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delay_probability").value(0.61))
                .andExpect(jsonPath("$.predicted_delayed").value(true))
                .andExpect(jsonPath("$.history_as_of").value("2024-03-31"));
    }

    @Test
    void rejectsInvalidHour() throws Exception {
        mvc.perform(post("/api/v1/predict/delay").contentType(MediaType.APPLICATION_JSON).content("""
                {"carrier":"WN","origin":"ATL","dest":"LGA","flight_date":"2024-04-02","dep_hour":31,"distance":762}"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc.perform(post("/api/v1/predict/delay").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest());
    }
}
