#ifndef NODYSSEY_NETWORK_H
#define NODYSSEY_NETWORK_H
#include <stdint.h>
// Input JSON is copied before start returns. Zero means invalid input/capacity exhausted.
uint64_t nodyssey_network_start(const char *request_json);
// NULL while pending. A completed result is removed and ownership passes to the caller.
char *nodyssey_network_take(uint64_t request_id);
void nodyssey_network_cancel(uint64_t request_id);
void nodyssey_network_free(char *result);
#endif
