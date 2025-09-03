//
//
// Copyright © 2021-2022 THALES. All rights reserved.
//

#ifndef D1CMemory_h
#define D1CMemory_h

#include <stddef.h>
#include <stdlib.h>

#ifdef __cplusplus
extern "C" {
#endif

int d1c_memset_s(void *v, size_t smax, int c, size_t n);
void* d1c_memset(void*  dst, int c, size_t n);
void d1c_free(void* p, size_t size);
void* d1c_malloc(size_t size);
void* d1c_memcpy(void* dst, const void* src, size_t n);

static __inline__ __attribute__((always_inline)) int d1c_validate(const void* ptr1, unsigned long len)
{
    const unsigned char* p1 = (const unsigned char*)ptr1;
    
    for(int i = 0; i < len; i++) {
        if(p1[i] < '0' || p1[i] > '9') {
            return 1;
        }
    }
    
    return 0;
}

static __inline__ __attribute__((always_inline)) int d1c_memcmp(const void* ptr1, const void* ptr2, unsigned long len)
{
    const unsigned char* p1 = (const unsigned char*)ptr1;
    const unsigned char* p2 = (const unsigned char*)ptr2;
    
    while (len-- > 0)
    {
        const int delta = *p1 - *p2;
        if (delta != 0)
        {
            return delta;
        }
        
        p1++;
        p2++;
    }
    
    return 0;
}

static __inline__ __attribute__((always_inline)) int d1c_is_first_entry(size_t entryLen, size_t cfmLen, size_t optionLen)
{
    if (entryLen == optionLen && cfmLen != optionLen) return 1;
    return 0;
}

static __inline__ __attribute__((always_inline)) void* d1c_revxor(void* dst, const void* src, const void* msk, size_t n)
{
    const uint8_t* s = (uint8_t*)src;
    const uint8_t* m = (uint8_t*)msk;
    uint8_t* d = (uint8_t*)dst;
    
    size_t i;
    size_t j = n-1;
    
    for (i = 0; i < n; ++i) {
        *d++ = *s++ ^ *(m+j);
        j--;
    }
    
    return dst;
}

static __inline__ __attribute__((always_inline)) void* d1c_xor(char* dst, const char* src, const char* msk, size_t n, size_t mLength)
{
    const char* s = (char*)src;
    const char* m = (char*)msk;
    uint8_t* d = (uint8_t*)dst;
    size_t i;
    for (i = 0; i < n; ++i) {
        *d++ = *s++ ^ m[i % mLength];
    }
    return dst;
}

static __inline__ __attribute__((always_inline)) void d1c_should_change_characters_in_range(const char* currentMask,
                                                                          const char* currentInputValueMasked,
                                                                          const char* tempMask,
                                                                          char* nextInputValueMasked,
                                                                          int newInputLength, int rangeLoc,
                                                                          int rangeLen, int stringLength,
                                                                          int FIXED_BYTE_LENGTH,
                                                                          const char *subCharSequenceToChangeAfterChange)
{
    
    // Part 1. copy and re-mask the chars before the point of change
    int part1End = rangeLoc;
    for (int i = 0; i < part1End; i++) {
        int reversedMaskByteIndex = FIXED_BYTE_LENGTH - 1 - i;
        nextInputValueMasked[i] = (char)((currentMask[reversedMaskByteIndex] ^ tempMask[reversedMaskByteIndex]) ^ (currentInputValueMasked[i]));
    }
    
    // Part 2. copy and mask the chars that are updated
    int part2End = part1End + stringLength;
    for (int i = part1End; i < part2End; i++) {
        int reversedMaskByteIndexAfter = FIXED_BYTE_LENGTH - 1 - i;
        nextInputValueMasked[i] = (char)(subCharSequenceToChangeAfterChange[i - part1End] ^ tempMask[reversedMaskByteIndexAfter]);
    }

    // Part 3. copy and re-mask the chars after the part that has been changed
    int part3Length = newInputLength-rangeLoc+stringLength;
    int part3End = part2End + part3Length;
    int oldPart2End = part1End + rangeLen;
    for (int i = part2End; i < part3End; i++) {
        int oldIndex = i - part2End + oldPart2End;
        int oldReversedMaskByteIndex = FIXED_BYTE_LENGTH - 1 - oldIndex;
        int reversedMaskByteIndex = FIXED_BYTE_LENGTH - 1 - i;
        nextInputValueMasked[i] = (char)((currentMask[oldReversedMaskByteIndex] ^ tempMask[reversedMaskByteIndex]) ^ currentInputValueMasked[oldIndex]);
    }
    
}

#ifdef __cplusplus
}
#endif

#endif /* D1CMemory_h */
