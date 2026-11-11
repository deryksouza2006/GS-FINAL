package br.com.fiap.citizentech.water;
import br.com.fiap.citizentech.croparea.repository.CropAreaRepository;
import br.com.fiap.citizentech.user.repository.UserRepository;
import br.com.fiap.citizentech.user.domain.UserRole;
import br.com.fiap.citizentech.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;

@Service @RequiredArgsConstructor @Transactional(readOnly=true) @PreAuthorize("hasRole('PRODUCER')")
public class ProfileService {
    private final ProfileRepository profiles;
    private final CropAreaRepository areas;
    private final UserRepository users;
    public record Response(Long id,Long cropAreaId,long revision,String soilTexture,double fieldCapacityPercent,
            double wiltingPointPercent,Double rootDepthCentimeters,Double sensorDepthCentimeters,String source,boolean demo) {
        static Response from(AgronomicProfile p) { return new Response(p.getId(),p.getCropArea().getId(),p.getRevision(),
                p.getSoilTexture(),p.getFieldCapacityPercent(),p.getWiltingPointPercent(),p.getRootDepthCentimeters(),
                p.getSensorDepthCentimeters(),p.getSource(),p.isDemo()); }
    }
    private Long owner(String email) {
        var u=users.findByEmailIgnoreCase(email).orElseThrow(this::missing);
        if(!u.isEnabled() || u.getRole()!=UserRole.PRODUCER) throw new BusinessException(HttpStatus.FORBIDDEN,"Acesso negado");
        return u.getId();
    }
    private BusinessException missing() { return new BusinessException(HttpStatus.NOT_FOUND,"Perfil ou cultivo não encontrado"); }
    public Response get(String email,Long area) {
        areas.findByIdAndFarmOwnerId(area,owner(email)).orElseThrow(this::missing);
        return Response.from(profiles.findFirstByCropAreaIdOrderByRevisionDesc(area).orElseThrow(this::missing));
    }
    @Transactional public Response put(String email,Long area,ProfileRequest request) {
        var crop=areas.findOwnedForUpdate(area,owner(email)).orElseThrow(this::missing);
        long revision=profiles.findFirstByCropAreaIdOrderByRevisionDesc(area).map(p->p.getRevision()+1).orElse(1L);
        return Response.from(profiles.saveAndFlush(new AgronomicProfile(crop,revision,request)));
    }
}
