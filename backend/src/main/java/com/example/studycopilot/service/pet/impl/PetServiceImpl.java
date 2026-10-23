package com.example.studycopilot.service.pet.impl;
import com.example.studycopilot.service.pet.PetService;
import com.example.studycopilot.service.focus.FocusService;
import com.example.studycopilot.dto.resp.pet.PetStatusResponse;
import com.example.studycopilot.support.db.OwnerStore;
import com.example.studycopilot.common.errorcode.ScErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class PetServiceImpl implements PetService {
 private final OwnerStore db;
 public PetStatusResponse status(){var row=db.one("select * from study_copilot.t_pet_status where user_id=?",db.owner());long exp=OwnerStore.number(row.get("total_exp"));int level=FocusService.level(exp);var r=new PetStatusResponse();r.setPetType(((Number)row.get("pet_type")).intValue());r.setTotalExp(exp);r.setLevel(level);r.setCurrentLevelExp(FocusService.THRESHOLDS[level-1]);r.setNextLevelExp(level==10?null:(int)FocusService.THRESHOLDS[level]);r.setMaxLevel(level==10);r.setStage(level<=3?"JUVENILE":level<=6?"GROWING":level<=9?"MATURE":"ULTIMATE");return r;}
 @Transactional public void switchType(Integer type){if(type==null||type<1||type>5)throw OwnerStore.error(ScErrorCode.PET_TYPE_INVALID);long u=db.owner();db.lock(u);db.jdbc.update("update study_copilot.t_pet_status set pet_type=?,version=version+1,update_time=now() where user_id=?",type,u);}
}
