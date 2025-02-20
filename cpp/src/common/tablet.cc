/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * License); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

#include "tablet.h"

#include <stdlib.h>

#include "utils/errno_define.h"

using namespace common;

namespace storage {

int Tablet::init() {
    ASSERT(timestamps_ == NULL);
    timestamps_ = (int64_t *)malloc(sizeof(int64_t) * max_row_num_);

    size_t schema_count = schema_vec_->size();
    std::pair<std::map<std::string, int>::iterator, bool> ins_res;
    for (size_t c = 0; c < schema_count; c++) {
        ins_res = schema_map_.insert(
            std::make_pair(schema_vec_->at(c).measurement_name_, c));
        if (!ins_res.second) {
            ASSERT(false);
            // maybe dup measurement_name
            return E_INVALID_ARG;
        }
    }
    ASSERT(schema_map_.size() == schema_count);

    value_matrix_ = (void **)malloc(sizeof(void *) * schema_count);
    for (size_t c = 0; c < schema_count; c++) {
        const MeasurementSchema &schema = schema_vec_->at(c);
        value_matrix_[c] =
            malloc(get_data_type_size(schema.data_type_) * max_row_num_);
    }

    bitmaps_ = new BitMap[schema_count];
    for (size_t c = 0; c < schema_count; c++) {
        bitmaps_[c].init(max_row_num_, /*init_as_zero=*/true);
    }
    return E_OK;
}

void Tablet::destroy() {
    if (timestamps_ != NULL) {
        free(timestamps_);
        timestamps_ = NULL;
    }
    if (value_matrix_ != NULL) {
        for (size_t c = 0; c < schema_vec_->size(); c++) {
            free(value_matrix_[c]);
        }
        free(value_matrix_);
        value_matrix_ = NULL;
    }
    if (bitmaps_ != NULL) {
        delete[] bitmaps_;
    }
}

int Tablet::add_timestamp(uint32_t row_index, int64_t timestamp) {
    ASSERT(timestamps_ != NULL);
    if (UNLIKELY(row_index >= static_cast<uint32_t>(max_row_num_))) {
        ASSERT(false);
        return E_OUT_OF_RANGE;
    }
    timestamps_[row_index] = timestamp;
    return E_OK;
}

template <typename T>
int Tablet::add_value(uint32_t row_index, uint32_t schema_index, T val) {
    int ret = common::E_OK;
    if (LIKELY(schema_index >= schema_vec_->size())) {
        ASSERT(false);
        ret = common::E_OUT_OF_RANGE;
    } else {
        const MeasurementSchema &schema = schema_vec_->at(schema_index);
        if (LIKELY(GetDataTypeFromTemplateType<T>() != schema.data_type_)) {
            ret = common::E_TYPE_NOT_MATCH;
        } else {
            T *column_values = (T *)value_matrix_[schema_index];
            column_values[row_index] = val;
            bitmaps_[schema_index].set(row_index); /* mark as non-null*/
        }
    }
    return ret;
}

template <typename T>
int Tablet::add_value(uint32_t row_index, const std::string &measurement_name,
                      T val) {
    int ret = common::E_OK;
    SchemaMapIterator find_iter = schema_map_.find(measurement_name);
    if (LIKELY(find_iter == schema_map_.end())) {
        ASSERT(false);
        ret = E_INVALID_ARG;
    } else {
        ret = add_value(row_index, find_iter->second, val);
    }
    return ret;
}

template int Tablet::add_value(uint32_t row_index, uint32_t schema_index,
                               bool val);
template int Tablet::add_value(uint32_t row_index, uint32_t schema_index,
                               int32_t val);
template int Tablet::add_value(uint32_t row_index, uint32_t schema_index,
                               int64_t val);
template int Tablet::add_value(uint32_t row_index, uint32_t schema_index,
                               float val);
template int Tablet::add_value(uint32_t row_index, uint32_t schema_index,
                               double val);

template int Tablet::add_value(uint32_t row_index,
                               const std::string &measurement_name, bool val);
template int Tablet::add_value(uint32_t row_index,
                               const std::string &measurement_name,
                               int32_t val);
template int Tablet::add_value(uint32_t row_index,
                               const std::string &measurement_name,
                               int64_t val);
template int Tablet::add_value(uint32_t row_index,
                               const std::string &measurement_name, float val);
template int Tablet::add_value(uint32_t row_index,
                               const std::string &measurement_name, double val);
}  // end namespace storage