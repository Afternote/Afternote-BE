#!/usr/bin/env python3
"""Validate generated receiver OpenAPI against MockMvc response samples."""
import copy
import json
from pathlib import Path
from openapi_schema_validator import OAS30Validator
from referencing import Registry, Resource
from referencing.jsonschema import DRAFT4

folder = Path('build/reports/receiver-contract')
document = json.loads((folder / 'openapi.json').read_text())
samples = json.loads((folder / 'responses.json').read_text())
registry = Registry().with_resource('urn:receiver', Resource.from_contents(document, default_specification=DRAFT4))
failures = []
rejected = 0
for sample in samples:
    path = sample['path'].replace('~', '~0').replace('/', '~1')
    response = document['paths'][sample['path']][sample['method']]['responses'][sample['status']]
    media = next(iter(response['content'])).replace('~', '~0').replace('/', '~1')
    pointer = f"urn:receiver#/paths/{path}/{sample['method']}/responses/{sample['status']}/content/{media}/schema"
    validator = OAS30Validator({'$ref': pointer}, registry=registry, format_checker=OAS30Validator.FORMAT_CHECKER)
    errors = list(validator.iter_errors(sample['payload']))
    invalid = copy.deepcopy(sample['payload'])
    if sample['status'] == '409':
        invalid['data'] = [1]
    elif sample['method'] == 'post':
        invalid['data'] = ['not-a-link-id']
    else:
        invalid['data']['credentials'] = {'id': 123, 'password': False}
    assert not validator.is_valid(invalid), 'Wrong data type accepted'
    rejected += 1
    if sample['status'] == '409':
        for field, value in (('status', 200), ('code', 200)):
            invalid = copy.deepcopy(sample['payload'])
            invalid[field] = value
            assert not validator.is_valid(invalid), 'Wrong conflict status/code accepted'
            rejected += 1
        invalid = copy.deepcopy(sample['payload'])
        invalid.pop('data')
        assert not validator.is_valid(invalid), 'Missing conflict data accepted'
        rejected += 1
    failures.extend(f"{sample['path']} {sample['status']} {list(e.path)}: {e.message}" for e in errors)
if failures:
    raise AssertionError('\n'.join(failures))
assert len(samples) >= 10
print(f'Receiver OpenAPI validation: {len(samples)} HTTP responses accepted; {rejected} invalid variants rejected')
