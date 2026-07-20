package se.medbo.examplatform.content.source;

import jakarta.validation.Valid; import jakarta.validation.constraints.*;
import java.time.LocalDate; import java.util.Map; import java.util.UUID;
import org.springframework.http.HttpStatus; import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/sources")
final class SourceController {
 private final SourceService service; SourceController(SourceService service){this.service=service;}
 record Request(@NotBlank String publisher,@NotBlank String title,String url,@NotBlank String sourceType,String documentVersion,LocalDate publicationDate,@NotNull LocalDate accessedAt,String copyrightNotes,String internalNotes,UUID replacementSourceId,Long version){SourceService.Input input(){return new SourceService.Input(publisher,title,url,sourceType,documentVersion,publicationDate,accessedAt,copyrightNotes,internalNotes,replacementSourceId,version);}}
 record VersionOnly(@NotNull @PositiveOrZero Long version){} record RetireRequest(UUID replacementSourceId,@NotNull @PositiveOrZero Long version){SourceService.Retire input(){return new SourceService.Retire(replacementSourceId,version);}}
 @GetMapping Map<String,Object> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String sort,@RequestParam(required=false) String search,@RequestParam(required=false) String publisher,@RequestParam(required=false) String sourceType,@RequestParam(required=false) String reviewStatus,@RequestParam(required=false) String status,@RequestParam(required=false) LocalDate accessedBefore,@RequestParam(required=false) LocalDate accessedAfter){return service.list(page,size,search,publisher,sourceType,reviewStatus,status,accessedBefore,accessedAfter);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) Map<String,Object> create(@Valid @RequestBody Request r){return service.create(r.input());}
 @GetMapping("/{id}") Map<String,Object> get(@PathVariable UUID id){return service.get(id);}
 @PutMapping("/{id}") Map<String,Object> update(@PathVariable UUID id,@Valid @RequestBody Request r){return service.update(id,r.input());}
 @PostMapping("/{id}/review") Map<String,Object> review(@PathVariable UUID id,@Valid @RequestBody VersionOnly r){return service.review(id,r.version());}
 @PostMapping("/{id}/require-update") Map<String,Object> requireUpdate(@PathVariable UUID id,@Valid @RequestBody VersionOnly r){return service.requireUpdate(id,r.version());}
 @PostMapping("/{id}/retire") Map<String,Object> retire(@PathVariable UUID id,@Valid @RequestBody RetireRequest r){return service.retire(id,r.input());}
}
