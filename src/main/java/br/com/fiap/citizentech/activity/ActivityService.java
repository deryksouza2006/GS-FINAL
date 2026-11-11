package br.com.fiap.citizentech.activity;

import br.com.fiap.citizentech.croparea.domain.CropArea;
import br.com.fiap.citizentech.croparea.repository.CropAreaRepository;
import br.com.fiap.citizentech.user.repository.UserRepository;
import br.com.fiap.citizentech.user.domain.User;
import br.com.fiap.citizentech.user.domain.UserRole;
import br.com.fiap.citizentech.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import java.time.Instant;

@Service @RequiredArgsConstructor @Transactional(readOnly=true) @PreAuthorize("hasRole('PRODUCER')")
public class ActivityService {
    private final ActivityRepository activities;
    private final CropAreaRepository areas;
    private final UserRepository users;
    private User producer(String email) {
        User u=users.findByEmailIgnoreCase(email).orElseThrow(this::missing);
        if(!u.isEnabled() || u.getRole()!=UserRole.PRODUCER) throw new BusinessException(HttpStatus.FORBIDDEN,"Acesso negado");
        return u;
    }
    private CropArea owned(Long id,User u) {
        CropArea area=areas.findOwnedForUpdate(id,u.getId()).orElseThrow(this::missing);
        if(area.getStatus()==br.com.fiap.citizentech.croparea.domain.CropAreaStatus.ARCHIVED)
            throw new BusinessException(HttpStatus.CONFLICT,"Cultivo arquivado: reative antes de alterar atividades");
        return area;
    }
    private BusinessException missing() { return new BusinessException(HttpStatus.NOT_FOUND,"Registro não encontrado"); }
    private FieldActivity activity(Long area,Long id) { return activities.findByIdAndCropAreaId(id,area).orElseThrow(this::missing); }
    @Transactional
    public ActivityResponse create(String email,Long areaId,ActivityRequest request) {
        User u=producer(email);
        return ActivityResponse.from(activities.saveAndFlush(new FieldActivity(owned(areaId,u),u,request)));
    }
    public Page<ActivityResponse> list(String email,Long areaId,FieldActivity.Type type,Instant from,Instant to,int page,int size) {
        areas.findByIdAndFarmOwnerId(areaId,producer(email).getId()).orElseThrow(this::missing);
        if(from!=null && to!=null && from.isAfter(to)) throw new BusinessException(HttpStatus.BAD_REQUEST,"Período inválido");
        Specification<FieldActivity> spec=(root,q,cb)-> {
            var p=new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            p.add(cb.equal(root.get("cropArea").get("id"),areaId));
            if(type!=null) p.add(cb.equal(root.get("type"),type));
            if(from!=null) p.add(cb.greaterThanOrEqualTo(root.get("performedAt"),from));
            if(to!=null) p.add(cb.lessThanOrEqualTo(root.get("performedAt"),to));
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return activities.findAll(spec,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"performedAt","id"))).map(ActivityResponse::from);
    }
    public ActivityResponse get(String email,Long area,Long id) {
        areas.findByIdAndFarmOwnerId(area,producer(email).getId()).orElseThrow(this::missing);
        return ActivityResponse.from(activity(area,id));
    }
    @Transactional public ActivityResponse update(String email,Long area,Long id,ActivityRequest request) {
        owned(area,producer(email)); FieldActivity a=activity(area,id); a.update(request);
        return ActivityResponse.from(activities.saveAndFlush(a));
    }
    @Transactional public void delete(String email,Long area,Long id) {
        owned(area,producer(email)); activities.delete(activity(area,id));
    }
}
