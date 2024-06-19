import time
from typing import Any, Dict, List

import xml.etree.ElementTree as etree
import requests


def run_nifi(username: str, password: str, template_path: str):
    print("NiFi: logging in")
    access_token = login(username, password)
    root_id = get_root_pg(access_token)["id"]
    print("NiFi: importing template")
    template_id = import_template(access_token, root_id, template_path)
    print("NiFi: instantiating template")
    instantiate_template(access_token, root_id, template_id)
    services = get_all_controller_services(access_token, root_id)
    print("NiFi: activating services")
    for service in services:
        enable_controller_service(
            access_token,
            service["id"],
            service["revision"],
        )
    # waiting for all controller services to start
    time.sleep(5)
    print("NiFi: scheduling process group")
    schedule_process_group(access_token, root_id)


nifi_base_url = "https://localhost:8443/nifi-api"


def login(username: str, password: str) -> str:
    data = {"username": username, "password": password}
    headers = {"Content-Type": "application/x-www-form-urlencoded"}
    response = requests.post(
        url=f"{nifi_base_url}/access/token",
        data=data,
        headers=headers,
        verify=False,
    )
    response.raise_for_status()
    return response.text


def get_root_pg(access_token: str) -> Any:
    headers = {"Authorization": f"Bearer {access_token}"}
    response = requests.get(
        url=f"{nifi_base_url}/process-groups/root",
        headers=headers,
        verify=False,
    )
    response.raise_for_status()
    return response.json()


def import_template(access_token: str, root_id: str, template_path: str) -> str:
    headers = {"Authorization": f"Bearer {access_token}"}
    response = requests.post(
        url=f"{nifi_base_url}/process-groups/{root_id}/templates/upload",
        headers=headers,
        files={"template": open(template_path, "rb")},
        verify=False,
    )
    response.raise_for_status()
    tree = etree.ElementTree(etree.fromstring(response.text))
    id_element = tree.find(".//id")
    if id_element is None:
        raise ValueError("Tag id was not found")
    id = id_element.text
    if id is None:
        raise ValueError("Tag id has no text")
    return id


def instantiate_template(
    access_token: str,
    root_id: str,
    template_id: str,
) -> Dict:
    headers = {
        "Authorization": f"Bearer {access_token}",
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    response = requests.post(
        url=f"{nifi_base_url}/process-groups/{root_id}/template-instance",
        headers=headers,
        json={"templateId": template_id, "originX": 0.0, "originY": 0.0},
        verify=False,
    )
    response.raise_for_status()
    return response.json()["flow"]


def schedule_process_group(access_token: str, root_id: str):
    headers = {"Authorization": f"Bearer {access_token}"}
    response = requests.put(
        url=f"{nifi_base_url}/flow/process-groups/{root_id}",
        headers=headers,
        json={"id": root_id, "state": "RUNNING"},
        verify=False,
    )
    response.raise_for_status()


def get_all_controller_services(access_token: str, root_id: str) -> List[Any]:
    headers = {
        "Authorization": f"Bearer {access_token}",
        "Accept": "application/json",
    }
    response = requests.get(
        url=f"{nifi_base_url}/flow/process-groups/{root_id}/controller-services",
        headers=headers,
        verify=False,
    )
    response.raise_for_status()
    return response.json()["controllerServices"]


def enable_controller_service(
    access_token: str,
    controller_service_id: str,
    service_revision: Any,
):
    headers = {"Authorization": f"Bearer {access_token}"}
    response = requests.put(
        url=f"{nifi_base_url}/controller-services/{controller_service_id}/run-status",
        headers=headers,
        json={"revision": service_revision, "state": "ENABLED"},
        verify=False,
    )
    response.raise_for_status()
