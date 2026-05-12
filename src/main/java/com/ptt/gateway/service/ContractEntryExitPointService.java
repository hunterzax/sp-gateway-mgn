package com.ptt.gateway.service;

import com.ptt.gateway.dto.EntryExitPointListDTO;
import com.ptt.gateway.model.ContractList;
import com.ptt.gateway.model.EntryExitPointList;
import com.ptt.gateway.repository.ContractListRepository;
import com.ptt.gateway.repository.EntryExitPointListRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractEntryExitPointService {

    private final EntryExitPointListRepository entryExitPointListRepository;
    private final ContractListRepository contractListRepository;

    @Transactional(readOnly = true)
    public List<EntryExitPointListDTO> getPointsByContract(Long contractID) {
        return entryExitPointListRepository.findAllByContractList_ContractID(contractID).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public EntryExitPointListDTO addPoint(Long contractID, EntryExitPointListDTO dto) {
        ContractList contract = contractListRepository.findById(contractID)
                .orElseThrow(() -> new RuntimeException("Contract not found: " + contractID));

        EntryExitPointList point = new EntryExitPointList();
        point.setContractList(contract);
        point.setPointName(dto.getPointName());
        point.setType(dto.getType());

        EntryExitPointList saved = entryExitPointListRepository.save(point);
        return mapToDTO(saved);
    }

    @Transactional
    public EntryExitPointListDTO updatePoint(String id, EntryExitPointListDTO dto) {
        EntryExitPointList point = entryExitPointListRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Point not found: " + id));

        if (dto.getPointName() != null)
            point.setPointName(dto.getPointName());
        if (dto.getType() != null)
            point.setType(dto.getType());

        EntryExitPointList updated = entryExitPointListRepository.save(point);
        return mapToDTO(updated);
    }

    @Transactional
    public void deletePoint(String id) {
        if (!entryExitPointListRepository.existsById(id)) {
            throw new RuntimeException("Point not found: " + id);
        }
        entryExitPointListRepository.deleteById(id);
    }

    private EntryExitPointListDTO mapToDTO(EntryExitPointList p) {
        return EntryExitPointListDTO.builder()
                .id(p.getId())
                .pointName(p.getPointName())
                .type(p.getType())
                .build();
    }
}
